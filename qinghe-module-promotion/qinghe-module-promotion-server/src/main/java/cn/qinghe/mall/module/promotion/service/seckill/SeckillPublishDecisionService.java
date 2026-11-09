package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.product.SeckillProductClient;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import cn.qinghe.mall.module.promotion.service.seckill.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原活动状态更新职责，按全部已确认划拨/关闭结果裁定，不从超时或单SKU成功推断整场成功。
 * 发布只变业务状态20；Redis epoch仍0、没有消费者入口，后续准入必须独立确认缓存就绪。
 * @author halfninety
 */
@Service
public class SeckillPublishDecisionService {
    private final SeckillPublishPreparationService preparations;
    private final SeckillProductClient products;
    private final RemoteSystemSecurityClient security;
    private final SeckillActivityMapper activities;
    private final SeckillPublishMapper publishes;
    private final SeckillPublishDecisionMapper decisions;
    private final SeckillRedisEpochMapper epochs;
    private final SeckillClearingMapper clearing;
    private final TransactionTemplate transaction;
    private final TransactionTemplate definitionRead;

    public SeckillPublishDecisionService(SeckillPublishPreparationService preparations,SeckillProductClient products,
            RemoteSystemSecurityClient security,SeckillActivityMapper activities,SeckillPublishMapper publishes,
            SeckillPublishDecisionMapper decisions,SeckillRedisEpochMapper epochs,SeckillClearingMapper clearing,PlatformTransactionManager manager) {
        this.preparations=preparations;this.products=products;this.security=security;this.activities=activities;this.publishes=publishes;this.decisions=decisions;
        this.epochs=epochs;
        this.clearing=clearing;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);transaction.setTimeout(15);
        definitionRead=new TransactionTemplate(manager);definitionRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definitionRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);definitionRead.setReadOnly(true);definitionRead.setTimeout(5);
    }

    /** 首次epoch只建立暂停初始化凭证；不初始化Redis、不接单，不提供缺Key自动重建。 */
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO prepareInitialEpoch(SeckillPublishReqDTO intent) {
        SeckillPublishPlanDTO plan=plan(intent);
        SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        return transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,plan); // 与撤回/后续受理共用活动父锁。
            SeckillRedisEpochDO previous=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            if (previous!=null) return initialReceipt(previous,record,activity);
            if (!Integer.valueOf(20).equals(activity.getStatus()) || !Long.valueOf(0).equals(activity.getRedisEpoch())
                    || activity.getEndTime()==null || !activity.getEndTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                    || !completed(actor,plan,"PUBLISH") || completed(actor,plan,"WITHDRAW"))
                throw exception0(409,"只有尚未初始化的当前已发布活动可以准备首次epoch");
            List<SeckillActivitySkuDO> skus=allConfirmed(actor,activity,plan,false);
            SeckillConfigurationSnapshotDTO frozen=JsonUtils.parseObject(record.getConfigurationSnapshot(),SeckillConfigurationSnapshotDTO.class);
            SeckillPublishedManifest manifest=definition(activity,skus,record,frozen);
            String snapshot=JsonUtils.toJsonString(manifest);
            SeckillRedisEpochDO row=new SeckillRedisEpochDO();row.setTenantId(actor.getTenantId());row.setActivityId(activity.getId());
            row.setGeneration(plan.getGeneration());row.setEpoch(1L);row.setPublishRequestId(plan.getRequestId());row.setActorId(actor.getId());
            row.setFromVersion(activity.getVersion());row.setToVersion(activity.getVersion()+1);row.setStatus(0);
            row.setDefinitionSnapshot(snapshot);row.setDefinitionDigest(DigestUtil.sha256Hex(snapshot));
            if (epochs.insertInitial(row)!=1 || epochs.beginInitial(actor.getTenantId(),activity.getId(),plan.getGeneration(),
                    plan.getRequestId(),activity.getVersion(),actor.getId().toString())!=1)
                throw exception0(409,"首次epoch准备未确认，请查询原发布请求");
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",plan.getRequestId());details.put("epoch",1);
            details.put("definitionDigest",row.getDefinitionDigest());
            if (activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),"EPOCH_INIT",row.getFromVersion(),row.getToVersion(),
                    JsonUtils.toJsonString(details))!=1) throw new IllegalStateException("首次epoch审计保存失败");
            SeckillRedisEpochDO saved=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            if (saved==null) throw new IllegalStateException("首次epoch凭证未读取");
            activity.setRedisEpoch(1L);activity.setVersion(row.getToVersion());return initialReceipt(saved,record,activity);
        });
    }

    private SeckillRedisEpochDO initialReceipt(SeckillRedisEpochDO row,SeckillPublishRecordDO record,SeckillActivityDO activity) {
        if (!Objects.equals(row.getTenantId(),record.getTenantId()) || !Objects.equals(row.getActivityId(),record.getActivityId())
                || !Objects.equals(row.getGeneration(),record.getGeneration()) || !Objects.equals(row.getPublishRequestId(),record.getRequestId())
                || !Objects.equals(row.getActorId(),record.getActorId()) || !Long.valueOf(1).equals(row.getEpoch())
                || !Long.valueOf(1).equals(activity.getRedisEpoch()) || row.getFromVersion()==null || row.getFromVersion()<record.getPreparedVersion()+1
                || row.getToVersion()==null || row.getToVersion()!=row.getFromVersion()+1 || activity.getVersion()<row.getToVersion()
                || !Arrays.asList(0,10,15,20,30,40).contains(row.getStatus()) || row.getCreateTime()==null || row.getDefinitionSnapshot()==null
                || (Arrays.asList(15,20,30).contains(row.getStatus())
                    ? row.getInitToken()==null || !row.getInitToken().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") || row.getInitDeadline()==null
                    : row.getInitToken()!=null || row.getInitDeadline()!=null))
            throw exception0(503,"原首次epoch凭证需核对，不能重新初始化");
        SeckillPublishedManifest original=JsonUtils.parseObject(row.getDefinitionSnapshot(),SeckillPublishedManifest.class);
        if (original==null || !Objects.equals(original.getTenantId(),row.getTenantId()) || !Objects.equals(original.getActivityId(),row.getActivityId())
                || !Objects.equals(original.getGeneration(),row.getGeneration()) || !Objects.equals(original.getPublishRequestId(),row.getPublishRequestId())
                || !Objects.equals(original.getFromVersion(),record.getFromVersion()) || !Objects.equals(original.getPreparedVersion(),record.getPreparedVersion())
                || !Objects.equals(DigestUtil.sha256Hex(JsonUtils.toJsonString(original)),row.getDefinitionDigest()))
            throw exception0(503,"原首次epoch定义摘要不一致");
        return row; // 重复只读原凭证，不重置已初始化过的状态/余额，也不重新执行CAS。
    }

    /** 仅确认不可变暂停seed；调用方已在事务外读取Redis，数据库提交不代表缓存此刻仍存在。 */
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO confirmInitialSeed(SeckillPublishReqDTO intent,String definitionDigest) {
        SeckillPublishPlanDTO plan=plan(intent);SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        return transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,plan);
            SeckillRedisEpochDO row=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            if (row==null) throw exception0(409,"原首次epoch尚未准备");
            initialReceipt(row,record,activity);
            if (!Objects.equals(row.getDefinitionDigest(),definitionDigest)) throw exception0(409,"暂停seed不属于原定义");
            if (Integer.valueOf(10).equals(row.getStatus())) return row;
            if (!Integer.valueOf(0).equals(row.getStatus())) throw exception0(409,"运行库存已进入后续阶段，不能回退seed阶段");
            if (!Integer.valueOf(20).equals(activity.getStatus()) || activity.getEndTime()==null
                    || !activity.getEndTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                    || !completed(actor,plan,"PUBLISH") || completed(actor,plan,"WITHDRAW"))
                throw exception0(409,"原发布已变化，不能确认暂停seed");
            allConfirmed(actor,activity,plan,false);
            if (epochs.markSeed(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId(),definitionDigest)!=1
                    || epochs.advanceStage(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId(),activity.getVersion(),actor.getId().toString())!=1)
                throw exception0(409,"暂停seed确认未完成，请按原epoch重查");
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",plan.getRequestId());details.put("epoch",1);
            details.put("definitionDigest",definitionDigest);
            if (activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),"EPOCH_SEED",activity.getVersion(),activity.getVersion()+1,
                    JsonUtils.toJsonString(details))!=1) throw new IllegalStateException("暂停seed审计保存失败");
            row.setStatus(10);return row;
        });
    }

    /** 原epoch只读查询，便于超时恢复；不会领取窗口或更改阶段。 */
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO getInitialEpoch(SeckillPublishReqDTO intent) {
        SeckillPublishPlanDTO plan=plan(intent);SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        return definitionRead.execute(tx -> {
            if (actor==null || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())
                    || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
                throw exception0(401,"原epoch查询身份已变化或到期");
            SeckillActivityDO activity=activities.get(actor.getTenantId(),plan.getActivityId());
            if (activity==null || !Objects.equals(activity.getGeneration(),plan.getGeneration()) || !Objects.equals(activity.getPublishOperationNo(),plan.getRequestId()))
                throw exception0(409,"原epoch已不属于当前发布");
            SeckillRedisEpochDO row=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            return row==null?null:initialReceipt(row,record,activity);
        });
    }

    /** Redis已通过原窗口/OPEN校验后确认DB就绪；不持父锁访问Redis，提交后仍须受理事务最终校验。 */
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO confirmInitialReady(SeckillPublishReqDTO intent,String token,String digest) {
        SeckillPublishPlanDTO plan=plan(intent);security.checkTenantOperation();SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        return transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,plan);SeckillRedisEpochDO row=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            if (row==null) throw exception0(409,"原epoch未准备");initialReceipt(row,record,activity);
            if (!Objects.equals(row.getInitToken(),token) || !Objects.equals(row.getDefinitionDigest(),digest)) throw exception0(409,"原就绪许可不一致");
            if (!Integer.valueOf(20).equals(activity.getStatus()) || activity.getEndTime()==null || !activity.getEndTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                throw exception0(409,"原活动已停止或结束，不能确认就绪");
            if (!completed(actor,plan,"PUBLISH") || completed(actor,plan,"WITHDRAW")) throw exception0(409,"原发布事实需核查，不能确认就绪");
            if (Integer.valueOf(20).equals(row.getStatus())) return row;
            if (!Integer.valueOf(15).equals(row.getStatus())) throw exception0(409,"原epoch不能进入就绪阶段");
            allConfirmed(actor,activity,plan,false);
            if (epochs.markReady(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId(),token,digest)!=1
                    || epochs.advanceStage(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId(),activity.getVersion(),actor.getId().toString())!=1)
                throw exception0(409,"原epoch就绪尚未确认，请按原号查询");
            epochAudit(actor,activity,plan,"EPOCH_READY");row.setStatus(20);return row;
        });
    }

    /** 先提交数据库拒绝新申请，再由协调层关闭Redis；保留所有库存/资金/请求义务。 */
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO stopInitialEpoch(SeckillPublishReqDTO intent,String reason) {
        if (reason==null || reason.trim().isEmpty() || reason.length()>500 || reason.chars().anyMatch(Character::isISOControl))
            throw exception0(400,"请填写有效的停止原因");
        SeckillPublishPlanDTO plan=plan(intent);SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        return transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,plan);SeckillRedisEpochDO row=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            if (row==null) throw exception0(409,"原epoch未准备");initialReceipt(row,record,activity);
            if (Integer.valueOf(30).equals(row.getStatus())) {
                if (!Integer.valueOf(30).equals(activity.getStatus()) || !reason.trim().equals(activity.getStopReason()) || !actor.getId().toString().equals(activity.getStoppedBy()))
                    throw exception0(409,"原停止记录不允许改写原因或操作者");
                return row;
            }
            if (!Arrays.asList(15,20).contains(row.getStatus()) || !Integer.valueOf(20).equals(activity.getStatus()))
                throw exception0(409,"当前epoch不能按运行阶段停止");
            if (epochs.markStopping(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId())!=1
                    || epochs.stopActivity(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId(),activity.getVersion(),actor.getId().toString(),reason.trim())!=1)
                throw exception0(409,"停止新接单尚未确认，请查询原epoch");
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",plan.getRequestId());details.put("epoch",1);details.put("reason",reason.trim());
            if (activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),"EPOCH_STOP",activity.getVersion(),activity.getVersion()+1,JsonUtils.toJsonString(details))!=1)
                throw new IllegalStateException("停止epoch审计保存失败");
            row.setStatus(30);row.setStopCacheState("PENDING");return row;
        });
    }
    private void epochAudit(LoginUser actor,SeckillActivityDO activity,SeckillPublishPlanDTO plan,String action) {
        Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",plan.getRequestId());details.put("epoch",1);
        if (activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),action,activity.getVersion(),activity.getVersion()+1,JsonUtils.toJsonString(details))!=1)
            throw new IllegalStateException("epoch阶段审计保存失败");
    }

    /** 领取固定30秒初始化窗口；重复只取原token/期限，不能续期或换号。 */
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
    public SeckillRedisEpochDO claimInitialRuntime(SeckillPublishReqDTO intent) {
        SeckillPublishPlanDTO plan=plan(intent);SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        return transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,plan);
            SeckillRedisEpochDO row=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            if (row==null) throw exception0(409,"原首次epoch尚未准备");
            initialReceipt(row,record,activity);
            if (Integer.valueOf(15).equals(row.getStatus())) return row;
            if (!Integer.valueOf(10).equals(row.getStatus()) || !Integer.valueOf(20).equals(activity.getStatus())
                    || !completed(actor,plan,"PUBLISH") || completed(actor,plan,"WITHDRAW"))
                throw exception0(409,"必须先确认原暂停seed，才能领取运行库存初始化窗口");
            allConfirmed(actor,activity,plan,false);
            if (epochs.claimRuntime(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId(),activity.getVersion(),UUID.randomUUID().toString())!=1
                    || epochs.advanceStage(actor.getTenantId(),activity.getId(),plan.getGeneration(),plan.getRequestId(),activity.getVersion(),actor.getId().toString())!=1)
                throw exception0(409,"运行库存初始化窗口未确认，请按原epoch重查");
            SeckillRedisEpochDO saved=epochs.initial(actor.getTenantId(),activity.getId(),plan.getGeneration());
            if (saved==null) throw new IllegalStateException("原初始化窗口未读取");
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",plan.getRequestId());details.put("epoch",1);
            details.put("initDeadline",saved.getInitDeadline().atOffset(ZoneOffset.UTC));
            if (activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),"EPOCH_RUNTIME",activity.getVersion(),activity.getVersion()+1,
                    JsonUtils.toJsonString(details))!=1) throw new IllegalStateException("运行库存初始化审计保存失败");
            activity.setVersion(activity.getVersion()+1);return initialReceipt(saved,record,activity);
        });
    }

    /** 原发布配置预载的内部只读来源；复用全部规格/回执校验，不改变epoch或就绪状态。 */
    public SeckillPublishedManifest publishedDefinition(SeckillPublishReqDTO intent) {
        SeckillPublishPlanDTO plan=plan(intent);
        SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");
        SeckillConfigurationSnapshotDTO frozen=JsonUtils.parseObject(record.getConfigurationSnapshot(),SeckillConfigurationSnapshotDTO.class);
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        return definitionRead.execute(tx -> {
            if (actor==null || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())
                    || !Objects.equals(actor.getTenantId(),record.getTenantId()) || !Objects.equals(actor.getId(),record.getActorId())
                    || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
                throw exception0(401,"发布定义查询身份已变化或到期");
            SeckillActivityDO activity=activities.get(actor.getTenantId(),plan.getActivityId());
            if (activity==null || !Integer.valueOf(20).equals(activity.getStatus()) || !Long.valueOf(0).equals(activity.getRedisEpoch())
                    || !Objects.equals(activity.getGeneration(),plan.getGeneration()) || !Objects.equals(activity.getPublishOperationNo(),plan.getRequestId())
                    || activity.getVersion()==null || activity.getVersion()<plan.getPreparedVersion()+1
                    || activity.getEndTime()==null || !activity.getEndTime().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                    || !completed(actor,plan,"PUBLISH") || completed(actor,plan,"WITHDRAW"))
                throw exception0(409,"仅当前已发布且准入尚未初始化的活动可预载定义");
            List<SeckillActivitySkuDO> skus=allConfirmed(actor,activity,plan,false);
            return definition(activity,skus,record,frozen);
        });
    }

    static SeckillPublishedManifest definition(SeckillActivityDO current,List<SeckillActivitySkuDO> skus,
            SeckillPublishRecordDO record,SeckillConfigurationSnapshotDTO frozen) {
        if (frozen==null || frozen.getActivity()==null || frozen.getSkus()==null || frozen.getSkus().size()!=skus.size())
            throw exception0(503,"原发布定义快照不完整");
        SeckillActivityDO original=frozen.getActivity();
        if (original.getStartTime()==null || original.getEndTime()==null
                || !Objects.equals(current.getId(),original.getId()) || !Objects.equals(current.getTenantId(),original.getTenantId())
                || !Objects.equals(current.getActivityNo(),original.getActivityNo()) || !Objects.equals(original.getVersion(),record.getFromVersion())
                || original.getGeneration()==null || original.getGeneration()+1!=record.getGeneration()
                || !Objects.equals(current.getProductSpuId(),original.getProductSpuId()) || !Objects.equals(current.getProductSpuVersion(),original.getProductSpuVersion())
                || !Objects.equals(current.getStartTime(),original.getStartTime()) || !Objects.equals(current.getEndTime(),original.getEndTime())
                || !Objects.equals(current.getSingleLimit(),original.getSingleLimit()) || !Objects.equals(current.getMemberLimit(),original.getMemberLimit()))
            throw exception0(503,"当前发布与原定义不一致");
        Map<Long,SeckillActivitySkuDO> byId=new HashMap<>();
        for (SeckillActivitySkuDO sku:frozen.getSkus()) {
            if (sku==null || sku.getSkuId()==null || byId.put(sku.getSkuId(),sku)!=null) throw exception0(503,"原定义规格重复或缺失");
        }
        SeckillPublishedManifest result=new SeckillPublishedManifest();result.setTenantId(record.getTenantId());result.setActivityId(record.getActivityId());
        result.setActivityNo(original.getActivityNo());result.setGeneration(record.getGeneration());result.setPublishRequestId(record.getRequestId());
        result.setFromVersion(record.getFromVersion());result.setPreparedVersion(record.getPreparedVersion());
        result.setProductSpuId(original.getProductSpuId());result.setProductSpuVersion(original.getProductSpuVersion());
        result.setStartAt(original.getStartTime().toInstant(ZoneOffset.UTC).toEpochMilli());result.setEndAt(original.getEndTime().toInstant(ZoneOffset.UTC).toEpochMilli());
        result.setSingleLimit(original.getSingleLimit());result.setMemberLimit(original.getMemberLimit());
        List<SeckillPublishedManifest.Sku> items=new ArrayList<>();
        for (SeckillActivitySkuDO sku:skus) {
            SeckillActivitySkuDO old=byId.get(sku.getSkuId());
            if (old==null || !Objects.equals(sku.getTenantId(),record.getTenantId()) || !Objects.equals(sku.getActivityId(),record.getActivityId())
                    || !Objects.equals(sku.getSkuVersion(),old.getSkuVersion()) || !Objects.equals(sku.getPriceCent(),old.getPriceCent())
                    || !Objects.equals(sku.getPlannedQuantity(),old.getPlannedQuantity())) throw exception0(503,"原定义规格与当前配置不一致");
            SeckillPublishedManifest.Sku item=new SeckillPublishedManifest.Sku();item.setSkuId(old.getSkuId());item.setSkuVersion(old.getSkuVersion());
            item.setPriceCent(old.getPriceCent());item.setPlannedQuantity(old.getPlannedQuantity());items.add(item);
        }
        result.setSkus(Collections.unmodifiableList(items));return result;
    }

    public SeckillPublishPlanDTO publish(SeckillPublishReqDTO intent) {
        SeckillPublishPlanDTO plan=plan(intent);LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        // 已完成的原请求只返回现状，不重新校验/写发布，也不在后续停止后重新开启。
        if (completed(actor,plan,"PUBLISH")) return plan;
        if (plan.getCurrentStatus()!=10) throw exception0(409,"只有原发布准备可以确认发布");
        security.checkTenantOperation();
        ProductSeckillAllocationReqDTO first=plan.getAllocations().get(0);
        SeckillProductSnapshotRespDTO snapshot=products.snapshot(first.getSpuId(),
                plan.getAllocations().stream().map(ProductSeckillAllocationReqDTO::getSkuId).collect(Collectors.toList()));
        SeckillPublishPlanDTO refreshed=plan(intent);
        if (!Objects.equals(actor.getId(),SecurityFrameworkUtils.getLoginUser().getId())) throw exception0(401,"发布操作身份已变化");
        transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,refreshed);
            if (completed(actor,refreshed,"PUBLISH")) return null;
            if (activity.getStatus()!=10 || !Objects.equals(activity.getVersion(),refreshed.getPreparedVersion())
                    || !activity.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                throw exception0(409,"原准备已停止、已变化或活动已经开始");
            List<SeckillActivitySkuDO> skus=allConfirmed(actor,activity,refreshed,false);
            verifyCurrentProduct(activity,skus,snapshot);
            lockGuards(actor,skus);
            for (SeckillActivitySkuDO sku:skus) {
                if (publishes.conflict(actor.getTenantId(),sku.getSkuId(),activity.getId(),activity.getStartTime(),activity.getEndTime())!=null)
                    throw exception0(409,"原规格出现活动时间冲突，请核查");
            }
            if (decisions.publish(actor.getTenantId(),activity.getId(),activity.getGeneration(),refreshed.getRequestId(),activity.getVersion(),actor.getId().toString())!=1)
                throw exception0(409,"发布裁定未确认，请查询原请求");
            audit(actor,activity,"PUBLISH",refreshed.getRequestId());return null;
        });
        return preparations.get(intent);
    }

    /** 只结清失败准备/明确开卖前撤回的60，全部product关闭墓碑和本地池一致才解除时间占用。 */
    public SeckillPublishPlanDTO settleFailed(SeckillPublishReqDTO intent) {
        SeckillPublishPlanDTO plan=plan(intent);LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,plan);
            if (completed(actor,plan,"SETTLE")) return null;
            if (activity.getStatus()!=60 || completed(actor,plan,"PUBLISH") && !completed(actor,plan,"WITHDRAW"))
                throw exception0(409,"只能结清失败准备或已确认的开卖前撤回");
            List<SeckillActivitySkuDO> skus=allConfirmed(actor,activity,plan,true);
            lockGuards(actor,skus);
            if (decisions.settle(actor.getTenantId(),activity.getId(),activity.getGeneration(),plan.getRequestId(),activity.getVersion(),actor.getId().toString())!=1)
                throw exception0(409,"失败发布结清未确认，请查询原请求");
            audit(actor,activity,"SETTLE",plan.getRequestId());return null;
        });
        return preparations.get(intent);
    }

    /** 尚未开卖且未初始化Redis准入时才允许走全额原号补偿；当前不用于已开卖/缓存已启用的停止。 */
    public SeckillPublishPlanDTO withdrawBeforeStart(SeckillPublishReqDTO intent,String reason) {
        SeckillPublishPlanDTO plan=plan(intent);LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (reason==null || reason.trim().isEmpty() || reason.length()>500 || reason.chars().anyMatch(Character::isISOControl))
            throw exception0(400,"请填写有效的撤回原因");
        String normalized=reason.trim();
        transaction.execute(tx -> {
            SeckillActivityDO activity=locked(actor,plan);
            if (completed(actor,plan,"WITHDRAW")) {
                if (!normalized.equals(activity.getStopReason()) || !actor.getId().toString().equals(activity.getStoppedBy()))
                    throw exception0(409,"撤回已经登记，不能改写原原因或操作者");
                return null;
            }
            if (activity.getStatus()!=20 || !completed(actor,plan,"PUBLISH") || !Long.valueOf(0).equals(activity.getRedisEpoch())
                    || activity.getStartTime()==null || !activity.getStartTime().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                throw exception0(409,"只允许撤回尚未开始且准入未初始化的已发布活动");
            List<SeckillActivitySkuDO> skus=allConfirmed(actor,activity,plan,false);
            lockGuards(actor,skus);
            if (decisions.withdraw(actor.getTenantId(),activity.getId(),activity.getGeneration(),plan.getRequestId(),
                    activity.getVersion(),actor.getId().toString(),normalized)!=1)
                throw exception0(409,"撤回未确认，请查询原请求");
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",plan.getRequestId());details.put("reason",normalized);
            if (activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),"WITHDRAW",activity.getVersion(),activity.getVersion()+1,
                    JsonUtils.toJsonString(details))!=1) throw new IllegalStateException("撤回审计保存失败");
            return null;
        });
        return preparations.get(intent);
    }

    /** 原失败发布SETTLE或运行代次CLEARING_DONE后恢复草稿；重复返回原恢复版本，不重复重置。 */
    public Long reopenSettled(SeckillPublishReqDTO intent) {
        SeckillPublishRecordDO record=preparations.getRecord(intent);
        if (record==null) throw exception0(404,"原发布记录不存在");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        Long recorded=decisions.reopenedVersion(actor.getTenantId(),record.getActivityId(),record.getRequestId());
        if (recorded!=null) return recorded;
        SeckillPublishPlanDTO plan=plan(intent);
        return transaction.execute(tx -> {
            // 先取得父锁再复查原恢复回执，避免另一提交完成后重复重置新草稿。
            if (activities.lock(actor.getTenantId(),record.getActivityId())==null) throw exception0(404,"原活动不存在");
            Long duplicate=decisions.reopenedVersion(actor.getTenantId(),record.getActivityId(),record.getRequestId());
            if (duplicate!=null) return duplicate;
            SeckillActivityDO activity=locked(actor,plan);
            if(Long.valueOf(1).equals(activity.getRedisEpoch()))return reopenCleared(actor,activity,plan);
            if (activity.getStatus()!=50 || !Long.valueOf(0).equals(activity.getRedisEpoch()) || !completed(actor,plan,"SETTLE"))
                throw exception0(409,"只有已确认关闭结清且准入未初始化的发布才能恢复草稿");
            List<SeckillActivitySkuDO> skus=allConfirmed(actor,activity,plan,true);
            lockGuards(actor,skus);
            if (decisions.resetSettledSkus(actor.getTenantId(),activity.getId())!=skus.size()
                    || decisions.reopen(actor.getTenantId(),activity.getId(),activity.getGeneration(),plan.getRequestId(),
                            activity.getVersion(),actor.getId().toString())!=1)
                throw exception0(409,"恢复草稿未确认，请查询原请求");
            audit(actor,activity,"REOPEN",plan.getRequestId());return activity.getVersion()+1;
        });
    }

    private Long reopenCleared(LoginUser actor,SeckillActivityDO activity,SeckillPublishPlanDTO plan) {
        Long tenant=actor.getTenantId();Integer generation=plan.getGeneration();
        if(!Integer.valueOf(50).equals(activity.getStatus()) || generation==Integer.MAX_VALUE
                || !Long.valueOf(1).equals(clearing.completed(tenant,activity.getId(),generation)))
            throw exception0(409,"运行代次尚未完成清算或不能创建新代次");
        SeckillRedisEpochDO epoch=epochs.initial(tenant,activity.getId(),generation);
        if(epoch==null || !Objects.equals(epoch.getPublishRequestId(),plan.getRequestId())
                || !(Integer.valueOf(30).equals(epoch.getStatus()) && "SYNCED".equals(epoch.getStopCacheState())
                || Integer.valueOf(40).equals(epoch.getStatus()) && "NOT_REQUIRED".equals(epoch.getStopCacheState())
                && epoch.getInitToken()==null && epoch.getInitDeadline()==null)
                || !Long.valueOf(0).equals(clearing.pendingObligations(tenant,activity.getId(),generation))
                || clearing.paidQuotaMatches(tenant,activity.getId(),generation)!=1)
            throw exception0(409,"原关闭或订单/消息/退款/归还义务尚待确认");
        List<SeckillClearingPoolDO> baseline=clearing.sealed(tenant,activity.getId(),generation);
        List<SeckillActivitySkuDO> pools=clearing.lockPools(tenant,activity.getId());
        if(baseline==null || baseline.isEmpty() || baseline.size()>200 || pools==null || baseline.size()!=pools.size()
                || baseline.size()!=plan.getAllocations().size())throw exception0(409,"原清算基线与当前规格不完整");
        for(int i=0;i<pools.size();i++) {
            SeckillClearingPoolDO c=baseline.get(i);SeckillActivitySkuDO p=pools.get(i);
            if(!Objects.equals(c.getSkuId(),p.getSkuId()) || !Objects.equals(c.getDefinitionDigest(),epoch.getDefinitionDigest())
                    || c.getActivityVersion()+1!=activity.getVersion() || !Objects.equals(c.getPoolVersion(),p.getVersion()))
                throw exception0(409,"原已结束清算版本或规格发生变化");
        }
        lockGuards(actor,pools);
        if(clearing.resetClearedSkus(tenant,activity.getId(),generation,plan.getRequestId())!=pools.size()
                || clearing.reopenCleared(tenant,activity.getId(),generation,plan.getRequestId(),activity.getVersion(),actor.getId().toString())!=1)
            throw exception0(409,"原清算代次恢复草稿未确认，请查询原请求");
        audit(actor,activity,"REOPEN",plan.getRequestId());return activity.getVersion()+1;
    }

    private List<SeckillActivitySkuDO> allConfirmed(LoginUser actor,SeckillActivityDO activity,SeckillPublishPlanDTO plan,boolean closing) {
        List<SeckillActivitySkuDO> skus=activities.skus(actor.getTenantId(),activity.getId());
        List<SeckillStockReceiptDO> receipts=decisions.latestAll(actor.getTenantId(),activity.getId(),plan.getGeneration());
        if (skus==null || receipts==null || skus.size()!=plan.getAllocations().size() || receipts.size()!=skus.size() || skus.isEmpty() || skus.size()>200)
            throw exception0(409,"原发布仍有规格未确认，不能裁定完成");
        Map<Long,SeckillStockReceiptDO> bySku=receipts.stream().collect(Collectors.toMap(SeckillStockReceiptDO::getSkuId,x->x));
        Map<Long,ProductSeckillAllocationReqDTO> requests=plan.getAllocations().stream().collect(Collectors.toMap(ProductSeckillAllocationReqDTO::getSkuId,x->x));
        for (SeckillActivitySkuDO sku:skus) {
            SeckillStockReceiptDO receipt=bySku.get(sku.getSkuId());ProductSeckillAllocationReqDTO original=requests.get(sku.getSkuId());
            if (receipt==null || original==null || !Objects.equals(sku.getAllocationOperationNo(),original.getOperationNo())
                    || !Objects.equals(sku.getSkuVersion(),original.getSkuVersion()) || !Objects.equals(activity.getProductSpuId(),original.getSpuId())
                    || !Objects.equals(activity.getProductSpuVersion(),original.getSpuVersion()) || !Objects.equals(sku.getPlannedQuantity(),original.getQuantity())
                    || !Objects.equals(receipt.getAllocatedQuantity(),sku.getAllocatedQuantity()) || !Objects.equals(receipt.getReturnedQuantity(),sku.getReturnedQuantity())
                    || receipt.getProductVersion()==null || receipt.getProductVersion()<1
                    || sku.getAllocatedQuantity()!=null && sku.getAllocatedQuantity()!=0 && !sku.getAllocatedQuantity().equals(original.getQuantity())
                    || !Integer.valueOf(0).equals(sku.getHeldQuantity()) || !Integer.valueOf(0).equals(sku.getSoldQuantity())
                    || !Integer.valueOf(0).equals(sku.getReturnPendingQuantity()) || sku.getAvailableQuantity()==null
                    || sku.getAllocatedQuantity()==null || sku.getReturnedQuantity()==null
                    || sku.getAvailableQuantity()!=sku.getAllocatedQuantity()-sku.getReturnedQuantity()) throw exception0(409,"原规格回执或额度池需核对");
            if (closing) {
                if (!Integer.valueOf(20).equals(receipt.getProductStatus()) || !Integer.valueOf(40).equals(sku.getAllocationStatus())
                        || !Integer.valueOf(0).equals(sku.getAvailableQuantity()) || !sku.getReturnedQuantity().equals(sku.getAllocatedQuantity()))
                    throw exception0(409,"原计划仍有规格未关闭或库存未归还");
            } else if (!Integer.valueOf(10).equals(receipt.getProductStatus()) || !Integer.valueOf(20).equals(sku.getAllocationStatus())
                    || !original.getQuantity().equals(sku.getAllocatedQuantity()) || !Integer.valueOf(0).equals(sku.getReturnedQuantity()))
                throw exception0(409,"原计划全部规格划拨成功后才能发布");
        }
        skus.sort(Comparator.comparing(SeckillActivitySkuDO::getSkuId));return skus;
    }
    private void verifyCurrentProduct(SeckillActivityDO activity,List<SeckillActivitySkuDO> skus,SeckillProductSnapshotRespDTO snapshot) {
        if (!Objects.equals(snapshot.getTenantId(),activity.getTenantId()) || !Objects.equals(snapshot.getSpuId(),activity.getProductSpuId())
                || !Objects.equals(snapshot.getSpuVersion(),activity.getProductSpuVersion()) || snapshot.getSkus().size()!=skus.size())
            throw exception0(409,"发布商品资料已变化，请停止准备并核查");
        Map<Long,SeckillProductSnapshotRespDTO.Sku> live=snapshot.getSkus().stream().collect(Collectors.toMap(SeckillProductSnapshotRespDTO.Sku::getSkuId,x->x));
        for (SeckillActivitySkuDO sku:skus) {
            if (!live.containsKey(sku.getSkuId()) || !Objects.equals(live.get(sku.getSkuId()).getSkuVersion(),sku.getSkuVersion()))
                throw exception0(409,"发布规格资料已变化，请停止准备并核查");
        }
    }
    private void lockGuards(LoginUser actor,List<SeckillActivitySkuDO> skus) {
        for (SeckillActivitySkuDO sku:skus) {
            if (!Objects.equals(publishes.lockGuard(actor.getTenantId(),sku.getSkuId()),sku.getSkuId()))
                throw exception0(503,"原规格互斥记录缺失，不能裁定完成");
        }
    }
    private void audit(LoginUser actor,SeckillActivityDO activity,String action,String requestId) {
        if (activities.audit(actor.getTenantId(),activity.getId(),actor.getId(),action,activity.getVersion(),activity.getVersion()+1,
                JsonUtils.toJsonString(Collections.singletonMap("requestId",requestId)))!=1) throw new IllegalStateException("发布裁定审计保存失败");
    }
    private boolean completed(LoginUser actor,SeckillPublishPlanDTO plan,String action) {
        return Long.valueOf(1).equals(decisions.completed(actor.getTenantId(),plan.getActivityId(),action,plan.getRequestId()));
    }
    private SeckillActivityDO locked(LoginUser actor,SeckillPublishPlanDTO plan) {
        if (actor==null || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())
                || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
            throw exception0(401,"活动操作身份已到期或变化");
        SeckillActivityDO activity=activities.lock(actor.getTenantId(),plan.getActivityId());
        if (activity==null || !Objects.equals(activity.getGeneration(),plan.getGeneration()) || !Objects.equals(activity.getPublishOperationNo(),plan.getRequestId())
                || activity.getVersion()==null || activity.getVersion()>=9_007_199_254_740_991L) throw exception0(409,"原活动发布代次已变化");
        return activity;
    }
    private SeckillPublishPlanDTO plan(SeckillPublishReqDTO intent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("发布裁定入口不能嵌套外部事务");
        SeckillPublishPlanDTO plan=preparations.get(intent);
        if (plan==null) throw exception0(404,"原发布准备不存在或尚未确认");return plan;
    }
}
