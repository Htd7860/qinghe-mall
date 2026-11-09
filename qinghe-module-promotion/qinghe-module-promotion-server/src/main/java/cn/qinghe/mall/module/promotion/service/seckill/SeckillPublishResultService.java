package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationReqDTO;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.SeckillPublishResultRespVO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.validation.Validator;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 纯本地查询本人原请求，无product/MQ/Redis调用，不推进任何发布/补偿状态。 */
@Service
public class SeckillPublishResultService {
    private final SeckillPublishPreparationService preparations;
    private final SeckillActivityMapper activities;
    private final SeckillPublishDecisionMapper decisions;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final TransactionTemplate read;

    public SeckillPublishResultService(SeckillPublishPreparationService preparations,SeckillActivityMapper activities,
            SeckillPublishDecisionMapper decisions,SecurityFrameworkService security,Validator validator,PlatformTransactionManager manager) {
        this.preparations=preparations;this.activities=activities;this.decisions=decisions;this.security=security;this.validator=validator;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }

    public SeckillPublishResultRespVO get(SeckillPublishReqDTO intent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("原结果查询不能嵌套外部事务");
        SeckillPublishRecordDO record=preparations.getRecord(intent); // 当前商家publish权限+原操作者/UUID/版本
        if (!security.hasPermission("promotion:seckill-activity:query")) throw exception0(403,"没有活动查询权限");
        if (record==null) return null; // 仅表示此刻未见已提交prepare，不授权换号或补偿。
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        ProductSeckillAllocationReqDTO[] requests=JsonUtils.parseObject(record.getRequestSnapshot(),ProductSeckillAllocationReqDTO[].class);
        if (requests==null || requests.length<1 || requests.length>200) throw incomplete();
        Set<Long> ids=new HashSet<>();
        for (ProductSeckillAllocationReqDTO request:requests) {
            if (request==null || !validator.validate(request).isEmpty() || !Objects.equals(request.getGeneration(),record.getGeneration())
                    || !ids.add(request.getSkuId())) throw incomplete();
        }
        return read.execute(tx -> {
            if (actor==null || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())
                    || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
                throw exception0(401,"活动查询身份已变化或到期");
            SeckillActivityDO activity=activities.get(record.getTenantId(),record.getActivityId());
            if (activity==null) throw exception0(404,"原活动不存在");
            List<SeckillStockReceiptDO> receipts=decisions.latestAll(record.getTenantId(),record.getActivityId(),record.getGeneration());
            if (receipts==null || receipts.size()>requests.length || receipts.stream().anyMatch(x->!ids.contains(x.getSkuId()))) throw incomplete();
            Map<Long,SeckillStockReceiptDO> bySku=receipts.stream().collect(Collectors.toMap(SeckillStockReceiptDO::getSkuId,x->x));
            SeckillPublishResultRespVO result=new SeckillPublishResultRespVO();result.setId(record.getActivityId());result.setRequestId(record.getRequestId());
            result.setGeneration(record.getGeneration());result.setFromVersion(record.getFromVersion());result.setPreparedVersion(record.getPreparedVersion());
            boolean current=Objects.equals(activity.getGeneration(),record.getGeneration()) && Objects.equals(activity.getPublishOperationNo(),record.getRequestId());
            result.setCurrentGeneration(current);result.setStatus(current?activity.getStatus():null);result.setVersion(current?activity.getVersion():null);
            result.setPublished(done(record,"PUBLISH"));result.setWithdrawn(done(record,"WITHDRAW"));result.setSettled(done(record,"SETTLE") || done(record,"CLEARING_DONE"));
            result.setReopenedVersion(decisions.reopenedVersion(record.getTenantId(),record.getActivityId(),record.getRequestId()));
            if (result.getWithdrawn() && !result.getPublished() || result.getReopenedVersion()!=null && !result.getSettled()) throw incomplete();
            List<SeckillPublishResultRespVO.Sku> skus=new ArrayList<>();
            for (ProductSeckillAllocationReqDTO request:requests) {
                SeckillPublishResultRespVO.Sku sku=new SeckillPublishResultRespVO.Sku();sku.setSkuId(request.getSkuId());sku.setQuantity(request.getQuantity());
                SeckillStockReceiptDO receipt=bySku.get(request.getSkuId());
                if (receipt!=null) {
                    if (receipt.getProductVersion()==null || receipt.getProductVersion()<1 || receipt.getProductVersion()>9_007_199_254_740_991L
                            || receipt.getAllocatedQuantity()==null || receipt.getReturnedQuantity()==null || receipt.getReturnedQuantity()<0
                            || receipt.getReturnedQuantity()>receipt.getAllocatedQuantity()
                            || receipt.getAllocatedQuantity()!=0 && !receipt.getAllocatedQuantity().equals(request.getQuantity())
                            || !Arrays.asList(10,20,30,50).contains(receipt.getProductStatus())
                            || Integer.valueOf(10).equals(receipt.getProductStatus()) && (receipt.getAllocatedQuantity()==0 || receipt.getReturnedQuantity()!=0)
                            || Arrays.asList(20,30).contains(receipt.getProductStatus()) && !receipt.getAllocatedQuantity().equals(receipt.getReturnedQuantity())
                            || Integer.valueOf(30).equals(receipt.getProductStatus()) && !receipt.getAllocatedQuantity().equals(request.getQuantity())
                            || Integer.valueOf(50).equals(receipt.getProductStatus()) && (!receipt.getAllocatedQuantity().equals(request.getQuantity())
                                || receipt.getReturnedQuantity()<=0 || receipt.getReturnedQuantity()>=receipt.getAllocatedQuantity())) throw incomplete();
                    sku.setProductStatus(receipt.getProductStatus());sku.setProductVersion(receipt.getProductVersion());
                    sku.setAllocatedQuantity(receipt.getAllocatedQuantity());sku.setReturnedQuantity(receipt.getReturnedQuantity());
                }
                // 未收到已提交回执保留null，不能补0假装已关闭或未占库存。
                skus.add(sku);
            }
            skus.sort(Comparator.comparing(SeckillPublishResultRespVO.Sku::getSkuId));result.setSkus(skus);result.setServerTime(System.currentTimeMillis());return result;
        });
    }
    private Boolean done(SeckillPublishRecordDO record,String action) {
        Long count=decisions.completed(record.getTenantId(),record.getActivityId(),action,record.getRequestId());
        if (count==null || count<0 || count>1) throw incomplete();return count==1;
    }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原发布结果不完整，请重新查询后核对"); }
}
