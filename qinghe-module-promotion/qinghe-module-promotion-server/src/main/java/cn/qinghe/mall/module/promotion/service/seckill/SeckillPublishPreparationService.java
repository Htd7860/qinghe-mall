package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.product.SeckillProductClient;
import cn.qinghe.mall.module.promotion.service.seckill.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.validation.Validator;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原validateProductExists/validateProductConflict的发布校验职责。
 * 仅持久化准备，不调用库存、不设置已发布；RPC必须在事务外，所有规格先冻结后按原号推进。
 * @author halfninety
 */
@Service
public class SeckillPublishPreparationService {
    private final SeckillActivityMapper activities;
    private final SeckillPublishMapper publishes;
    private final SeckillProductClient products;
    private final SecurityFrameworkService security;
    private final RemoteSystemSecurityClient remote;
    private final Validator validator;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public SeckillPublishPreparationService(SeckillActivityMapper activities,SeckillPublishMapper publishes,SeckillProductClient products,
            SecurityFrameworkService security,RemoteSystemSecurityClient remote,Validator validator,PlatformTransactionManager manager) {
        this.activities=activities;this.publishes=publishes;this.products=products;this.security=security;this.remote=remote;this.validator=validator;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(15);
    }

    public SeckillPublishPlanDTO prepare(SeckillPublishReqDTO body) {
        LoginUser actor=actor(body);
        SeckillPublishPlanDTO previous=read.execute(tx -> original(body,actor));
        if (previous!=null) return previous;
        remote.checkTenantOperation();
        List<SeckillActivitySkuDO> before=read.execute(tx -> {
            SeckillActivityDO activity=activities.get(actor.getTenantId(),body.getId());draft(activity,body);
            List<SeckillActivitySkuDO> skus=activities.skus(actor.getTenantId(),body.getId());unused(skus);return skus;
        });
        SeckillActivityDO beforeActivity=read.execute(tx -> activities.get(actor.getTenantId(),body.getId()));
        draft(beforeActivity,body);
        SeckillProductSnapshotRespDTO snapshot=products.snapshot(beforeActivity.getProductSpuId(),
                before.stream().map(SeckillActivitySkuDO::getSkuId).collect(Collectors.toList()));
        LoginUser current=actor(body);
        if (!Objects.equals(current.getId(),actor.getId()) || !Objects.equals(current.getTenantId(),actor.getTenantId()))
            throw exception0(401,"发布操作身份已变化");
        return write.execute(tx -> {
            fresh(actor);
            SeckillActivityDO activity=activities.lock(actor.getTenantId(),body.getId());
            SeckillPublishPlanDTO duplicate=original(body,actor);
            if (duplicate!=null) return duplicate;
            draft(activity,body);
            List<SeckillActivitySkuDO> skus=activities.skus(actor.getTenantId(),body.getId());unused(skus);
            verifySnapshot(activity,skus,snapshot);
            // Mapper按SKU升序返回；同SKU的持久guard即使原先无活动也能阻止并发检查均通过。
            for (SeckillActivitySkuDO sku:skus) {
                publishes.ensureGuard(actor.getTenantId(),sku.getSkuId());
                if (!Objects.equals(publishes.lockGuard(actor.getTenantId(),sku.getSkuId()),sku.getSkuId()))
                    throw new IllegalStateException("活动规格互斥行缺失");
            }
            for (SeckillActivitySkuDO sku:skus) {
                if (publishes.conflict(actor.getTenantId(),sku.getSkuId(),activity.getId(),activity.getStartTime(),activity.getEndTime())!=null)
                    throw exception0(409,"所选规格已有时间重叠的活动或未结清发布");
            }
            List<ProductSeckillAllocationReqDTO> allocations=new ArrayList<>();
            for (SeckillActivitySkuDO sku:skus) {
                ProductSeckillAllocationReqDTO request=new ProductSeckillAllocationReqDTO();
                request.setActivityNo(activity.getActivityNo());request.setGeneration(activity.getGeneration()+1);
                request.setOperationNo(UUID.randomUUID().toString());request.setSpuId(activity.getProductSpuId());
                request.setSkuId(sku.getSkuId());request.setSpuVersion(activity.getProductSpuVersion());request.setSkuVersion(sku.getSkuVersion());
                request.setQuantity(sku.getPlannedQuantity());
                if (!validator.validate(request).isEmpty() || publishes.prepareSku(actor.getTenantId(),activity.getId(),sku.getSkuId(),
                        sku.getVersion(),request.getOperationNo())!=1) throw exception0(409,"活动规格已变化，不能冻结发布");
                allocations.add(request);
            }
            if (publishes.prepare(actor.getTenantId(),activity.getId(),body.getExpectedVersion(),body.getRequestId(),actor.getId().toString())!=1)
                throw exception0(409,"活动已变化或已经开始，不能进入发布准备");
            SeckillPublishRecordDO record=new SeckillPublishRecordDO();record.setTenantId(actor.getTenantId());record.setRequestId(body.getRequestId());
            record.setActivityId(activity.getId());record.setActorId(actor.getId());record.setFromVersion(body.getExpectedVersion());
            record.setPreparedVersion(body.getExpectedVersion()+1);record.setGeneration(activity.getGeneration()+1);
            record.setRequestSnapshot(JsonUtils.toJsonString(allocations));
            SeckillConfigurationSnapshotDTO frozen=new SeckillConfigurationSnapshotDTO();frozen.setActivity(activity);frozen.setSkus(skus);
            record.setConfigurationSnapshot(JsonUtils.toJsonString(frozen));
            if (publishes.insert(record)!=1 || activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),"PREPARE",
                    record.getFromVersion(),record.getPreparedVersion(),JsonUtils.toJsonString(record))!=1)
                throw new IllegalStateException("发布准备凭证或审计保存失败");
            return original(body,actor);
        });
    }

    /** 原请求只读；null不表示没有在途prepare，不授权换号或补偿。 */
    public SeckillPublishPlanDTO get(SeckillPublishReqDTO body) {
        LoginUser actor=actor(body);return read.execute(tx -> original(body,actor));
    }

    /** 服务器原历史只读，允许活动已进入新代次；写调用仍只能使用get的当前代次检查。 */
    public SeckillPublishRecordDO getRecord(SeckillPublishReqDTO body) {
        LoginUser actor=actor(body);return read.execute(tx -> ownedRecord(body,actor));
    }

    private SeckillPublishRecordDO ownedRecord(SeckillPublishReqDTO body,LoginUser actor) {
        fresh(actor);SeckillPublishRecordDO record=publishes.get(actor.getTenantId(),body.getRequestId());
        if (record==null) return null;
        if (!Objects.equals(record.getActivityId(),body.getId()) || !Objects.equals(record.getActorId(),actor.getId())
                || !Objects.equals(record.getFromVersion(),body.getExpectedVersion())) throw exception0(409,"发布原请求主体或版本不一致");
        SeckillConfigurationSnapshotDTO frozen=JsonUtils.parseObject(record.getConfigurationSnapshot(),SeckillConfigurationSnapshotDTO.class);
        if (frozen==null || frozen.getActivity()==null || frozen.getSkus()==null || frozen.getSkus().isEmpty() || frozen.getSkus().size()>200
                || !Objects.equals(frozen.getActivity().getId(),record.getActivityId()) || !Objects.equals(frozen.getActivity().getTenantId(),record.getTenantId())
                || !Objects.equals(frozen.getActivity().getVersion(),record.getFromVersion())) throw exception0(503,"原发布配置快照不完整");
        return record;
    }

    private SeckillPublishPlanDTO original(SeckillPublishReqDTO body,LoginUser actor) {
        SeckillPublishRecordDO record=ownedRecord(body,actor);
        if (record==null) return null;
        if (!Objects.equals(record.getActivityId(),body.getId()) || !Objects.equals(record.getActorId(),actor.getId())
                || !Objects.equals(record.getFromVersion(),body.getExpectedVersion())) throw exception0(409,"发布原请求主体或版本不一致");
        ProductSeckillAllocationReqDTO[] requests=JsonUtils.parseObject(record.getRequestSnapshot(),ProductSeckillAllocationReqDTO[].class);
        if (requests==null || requests.length<1 || requests.length>200 || record.getPreparedVersion()!=record.getFromVersion()+1
                || record.getGeneration()==null || record.getGeneration()<1) throw exception0(503,"原发布准备凭证不完整");
        SeckillActivityDO activity=activities.get(actor.getTenantId(),body.getId());
        if (activity==null || !Objects.equals(activity.getPublishOperationNo(),record.getRequestId())
                || !Objects.equals(activity.getGeneration(),record.getGeneration()) || activity.getVersion()<record.getPreparedVersion())
            throw exception0(409,"原发布已进入历史，不能对当前活动执行旧代次库存命令");
        Set<Long> skuIds=new HashSet<>();Set<String> operations=new HashSet<>();
        for (ProductSeckillAllocationReqDTO request:requests) {
            if (request==null || !validator.validate(request).isEmpty() || !Objects.equals(request.getActivityNo(),activity.getActivityNo())
                    || !Objects.equals(request.getGeneration(),record.getGeneration()) || !skuIds.add(request.getSkuId())
                    || !operations.add(request.getOperationNo())) throw exception0(503,"原发布库存请求不完整");
        }
        SeckillPublishPlanDTO result=new SeckillPublishPlanDTO();result.setActivityId(record.getActivityId());result.setRequestId(record.getRequestId());
        result.setGeneration(record.getGeneration());result.setPreparedVersion(record.getPreparedVersion());result.setCurrentStatus(activity.getStatus());
        result.setCurrentVersion(activity.getVersion());result.setAllocations(Arrays.asList(requests));return result;
    }

    private void verifySnapshot(SeckillActivityDO activity,List<SeckillActivitySkuDO> skus,SeckillProductSnapshotRespDTO snapshot) {
        if (!Objects.equals(activity.getProductSpuId(),snapshot.getSpuId()) || !Objects.equals(activity.getProductSpuVersion(),snapshot.getSpuVersion())
                || snapshot.getSkus().size()!=skus.size()) throw exception0(409,"活动商品已变化，请先重新保存草稿");
        Map<Long,SeckillProductSnapshotRespDTO.Sku> current=snapshot.getSkus().stream().collect(Collectors.toMap(SeckillProductSnapshotRespDTO.Sku::getSkuId,x->x));
        for (SeckillActivitySkuDO sku:skus) {
            SeckillProductSnapshotRespDTO.Sku live=current.get(sku.getSkuId());
            ProductTradeSkuRespDTO.Property[] properties=JsonUtils.parseObject(sku.getSpecSnapshot(),ProductTradeSkuRespDTO.Property[].class);
            if (live==null || !Objects.equals(live.getSkuVersion(),sku.getSkuVersion()) || properties==null
                    || !live.getProperties().equals(Arrays.asList(properties))) throw exception0(409,"活动规格已变化，请先重新保存草稿");
        }
    }
    private void unused(List<SeckillActivitySkuDO> skus) {
        if (skus==null || skus.isEmpty() || skus.size()>200) throw exception0(409,"活动规格不完整");
        for (SeckillActivitySkuDO sku:skus) {
            if (!Integer.valueOf(0).equals(sku.getAllocationStatus()) || sku.getAllocationOperationNo()!=null || sku.getVersion()==null
                    || sku.getVersion()<0 || sku.getVersion()>=9_007_199_254_740_991L || sku.getPlannedQuantity()==null || sku.getPlannedQuantity()<1
                    || !Integer.valueOf(0).equals(sku.getAllocatedQuantity()) || !Integer.valueOf(0).equals(sku.getAvailableQuantity())
                    || !Integer.valueOf(0).equals(sku.getHeldQuantity()) || !Integer.valueOf(0).equals(sku.getSoldQuantity())
                    || !Integer.valueOf(0).equals(sku.getReturnedQuantity()) || !Integer.valueOf(0).equals(sku.getReturnPendingQuantity()))
                throw exception0(409,"活动已有库存义务，不能重新准备发布");
        }
    }
    private void draft(SeckillActivityDO activity,SeckillPublishReqDTO body) {
        if (activity==null) throw exception0(404,"活动不存在");
        if (!Objects.equals(activity.getVersion(),body.getExpectedVersion()) || !Integer.valueOf(0).equals(activity.getStatus())
                || activity.getGeneration()==null || activity.getGeneration()<0 || activity.getGeneration()==Integer.MAX_VALUE || activity.getPublishOperationNo()!=null
                || activity.getStartTime()==null || !activity.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
            throw exception0(409,"活动已变化、已经开始或尚未恢复为可编辑草稿");
    }
    private LoginUser actor(SeckillPublishReqDTO body) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("发布准备入口不能嵌套外部事务");
        if (body==null || !validator.validate(body).isEmpty()) throw exception0(400,"发布原请求编号或版本不正确");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (actor==null) throw exception0(401,"请先登录");
        if (!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L
                || actor.getTenantId()==null || actor.getTenantId()<=0 || actor.getTenantId()>9_007_199_254_740_991L)
            throw exception0(403,"发布准备仅限当前商家员工");
        fresh(actor);if (!security.hasPermission("promotion:seckill-activity:publish")) throw exception0(403,"没有活动发布权限");return actor;
    }
    private void fresh(LoginUser actor) {
        if (TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())
                || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw exception0(401,"活动发布身份已到期或变化");
    }
}
