package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.product.SeckillStockClient;
import cn.qinghe.mall.module.promotion.service.seckill.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationRespDTO.*;

/**
 * 择取原活动库存更新/取消恢复职责，接本项目持久发布计划，单次只推进一个SKU。
 * product RPC在事务外，已确认事实在原活动父锁下记账；未确认不能当回滚，当前不设置已发布。
 * @author halfninety
 */
@Service
public class SeckillPublishStockService {
    private final SeckillPublishPreparationService preparations;
    private final SeckillStockClient stock;
    private final SeckillActivityMapper activities;
    private final SeckillStockResultMapper results;
    private final TransactionTemplate transaction;

    public SeckillPublishStockService(SeckillPublishPreparationService preparations,SeckillStockClient stock,
            SeckillActivityMapper activities,SeckillStockResultMapper results,PlatformTransactionManager manager) {
        this.preparations=preparations;this.stock=stock;this.activities=activities;this.results=results;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setTimeout(10);
    }

    /** 先查原号；查询成功且未见记录才以同一原号请求划拨，无网络自动重试。 */
    public SeckillPublishPlanDTO advance(SeckillPublishReqDTO intent,Long skuId) {
        SeckillPublishPlanDTO plan=plan(intent);LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        ProductSeckillAllocationRespDTO response=stock.result(intent,skuId);
        if (response==null) response=stock.allocate(intent,skuId);
        record(plan,actor,response);
        return preparations.get(intent);
    }

    /** 显式停止未发布准备；不调用任何库存RPC，也不声称库存已经归还。 */
    public SeckillPublishPlanDTO cancelPreparation(SeckillPublishReqDTO intent,String reason) {
        SeckillPublishPlanDTO plan=plan(intent);LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (reason==null || reason.trim().isEmpty() || reason.length()>500 || reason.chars().anyMatch(Character::isISOControl))
            throw exception0(400,"请填写有效的发布停止原因");
        transaction.execute(tx -> {
            SeckillActivityDO activity=locked(plan,actor);
            if (activity.getStatus()==60) {
                if (!reason.trim().equals(activity.getStopReason()) || !actor.getId().toString().equals(activity.getStoppedBy()))
                    throw exception0(409,"原发布已停止，不能改写原原因或操作主体");
                return null;
            }
            fail(activity,actor,reason.trim());return null;
        });
        return preparations.get(intent);
    }

    /** 失败60后对每个原计划SKU都要执行；查询null也发送原号补偿以建立关闭墓碑。 */
    public SeckillPublishPlanDTO compensate(SeckillPublishReqDTO intent,Long skuId) {
        SeckillPublishPlanDTO plan=plan(intent);LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (plan.getCurrentStatus()!=60) throw exception0(409,"必须先停止本代发布准备");
        ProductSeckillAllocationRespDTO response=stock.result(intent,skuId);
        if (response==null || response.getStatus()!=COMPENSATED) response=stock.compensate(intent,skuId);
        record(plan,actor,response);return preparations.get(intent);
    }

    private void record(SeckillPublishPlanDTO plan,LoginUser actor,ProductSeckillAllocationRespDTO response) {
        if (response==null || !Arrays.asList(ALLOCATED,COMPENSATED).contains(response.getStatus()))
            throw exception0(409,"原划拨处于其他阶段，不能按发布准备写入");
        ProductSeckillAllocationReqDTO original=plan.getAllocations().stream().filter(x->Objects.equals(x.getSkuId(),response.getSkuId())).findFirst()
                .orElseThrow(()->exception0(503,"库存响应不属于原发布计划"));
        if (!Objects.equals(original.getOperationNo(),response.getOperationNo())) throw exception0(503,"原划拨业务号不一致");
        transaction.execute(tx -> {
            SeckillActivityDO activity=locked(plan,actor);
            List<SeckillActivitySkuDO> skus=activities.skus(activity.getTenantId(),activity.getId());
            SeckillActivitySkuDO sku=skus.stream().filter(x->Objects.equals(x.getSkuId(),original.getSkuId())).findFirst()
                    .orElseThrow(()->exception0(503,"原发布规格缺失"));
            if (!Objects.equals(sku.getAllocationOperationNo(),original.getOperationNo()) || sku.getHeldQuantity()!=0 || sku.getSoldQuantity()!=0
                    || sku.getReturnPendingQuantity()!=0 || sku.getVersion()>=9_007_199_254_740_991L)
                throw exception0(409,"原活动已有其他库存义务，不能按准备阶段记账");
            SeckillStockReceiptDO last=results.latest(activity.getTenantId(),activity.getId(),plan.getGeneration(),sku.getSkuId());
            if (last!=null && response.getVersion()<=last.getProductVersion()) {
                if (response.getVersion().equals(last.getProductVersion())
                        && (!Objects.equals(response.getStatus(),last.getProductStatus())
                            || !Objects.equals(response.getAllocatedQuantity(),last.getAllocatedQuantity())
                            || !Objects.equals(response.getReturnedQuantity(),last.getReturnedQuantity())))
                    throw exception0(503,"同一商品库存版本返回不同结果，需核查");
                return null; // 较旧迟到响应不能覆盖新补偿；重复同版本不重复流水。
            }
            if (response.getAllocatedQuantity()<sku.getAllocatedQuantity() || response.getReturnedQuantity()<sku.getReturnedQuantity()
                    || Integer.valueOf(40).equals(sku.getAllocationStatus()) && response.getStatus()!=COMPENSATED)
                throw exception0(503,"原划拨结果发生回退，需核查");
            if (response.getStatus()==COMPENSATED && activity.getStatus()==10) fail(activity,actor,"原划拨已关闭");
            int available=response.getAllocatedQuantity()-response.getReturnedQuantity();
            int allocatedDelta=response.getAllocatedQuantity()-sku.getAllocatedQuantity();
            int availableDelta=available-sku.getAvailableQuantity();
            int returnedDelta=response.getReturnedQuantity()-sku.getReturnedQuantity();
            if (results.apply(activity.getTenantId(),activity.getId(),sku.getSkuId(),sku.getVersion(),original.getOperationNo(),
                    response.getAllocatedQuantity(),available,response.getReturnedQuantity(),response.getStatus()==ALLOCATED?20:40)!=1)
                throw exception0(409,"活动规格结果已变化，请查询原结果");
            SeckillStockReceiptDO receipt=new SeckillStockReceiptDO();receipt.setTenantId(activity.getTenantId());receipt.setActivityId(activity.getId());
            receipt.setGeneration(plan.getGeneration());receipt.setSkuId(sku.getSkuId());receipt.setProductVersion(response.getVersion());
            receipt.setProductStatus(response.getStatus());receipt.setAllocatedQuantity(response.getAllocatedQuantity());receipt.setReturnedQuantity(response.getReturnedQuantity());
            if (results.insert(receipt)!=1 || results.journal(activity.getTenantId(),original.getOperationNo()+":"+response.getVersion(),activity.getId(),
                    plan.getGeneration(),sku.getSkuId(),response.getStatus()==ALLOCATED?"ALLOCATE":"COMPENSATE",allocatedDelta,availableDelta,returnedDelta,original.getOperationNo())!=1)
                throw new IllegalStateException("原库存回执与活动额度流水保存失败");
            return null;
        });
    }
    private void fail(SeckillActivityDO activity,LoginUser actor,String reason) {
        if (activity.getStatus()!=10 || activity.getVersion()>=9_007_199_254_740_991L) throw exception0(409,"当前活动不允许按未发布准备停止");
        if (results.fail(activity.getTenantId(),activity.getId(),activity.getGeneration(),activity.getPublishOperationNo(),activity.getVersion(),actor.getId().toString(),reason)!=1
                || activities.audit(activity.getTenantId(),activity.getId(),actor.getId(),"FAIL",activity.getVersion(),activity.getVersion()+1,
                        JsonUtils.toJsonString(Collections.singletonMap("reason",reason)))!=1) throw exception0(409,"发布停止状态未确认，请查询原请求");
        activity.setStatus(60);activity.setVersion(activity.getVersion()+1);
    }
    private SeckillActivityDO locked(SeckillPublishPlanDTO plan,LoginUser actor) {
        if (actor==null || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())
                || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw exception0(401,"发布操作身份已变化或到期");
        SeckillActivityDO row=activities.lock(actor.getTenantId(),plan.getActivityId());
        if (row==null || !Objects.equals(row.getGeneration(),plan.getGeneration()) || !Objects.equals(row.getPublishOperationNo(),plan.getRequestId())
                || !Arrays.asList(10,60).contains(row.getStatus())) throw exception0(409,"原活动已退出发布准备/失败阶段");
        return row;
    }
    private SeckillPublishPlanDTO plan(SeckillPublishReqDTO intent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("库存推进入口不能嵌套事务");
        SeckillPublishPlanDTO plan=preparations.get(intent);
        if (plan==null) throw exception0(404,"原发布准备不存在或尚未确认");return plan;
    }
}
