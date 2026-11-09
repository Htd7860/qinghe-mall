package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.promotion.controller.platform.seckill.vo.PlatformSeckillStopReqVO;
import cn.qinghe.mall.module.promotion.controller.platform.seckill.vo.PlatformSeckillStopRespVO;
import java.time.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.SeckillInitialRuntimeRedisDAO;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillConfigurationSnapshotDTO;
import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationReqDTO;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.util.*;

/** 复用stopInitialEpoch的先DB停止/后Redis关闭；不借用户身份或提前结清库存义务。 */
@Service @Slf4j
@ConditionalOnProperty(prefix="qinghe.promotion.seckill-results",name="enabled",havingValue="true")
public class SeckillActivityExpiryService {
    private final SeckillActivityMapper activities;
    private final SeckillRedisEpochMapper epochs;
    private final SeckillPublishMapper publishes;
    private final SeckillPublishDecisionMapper decisions;
    private final SeckillAdmissionMapper admission;
    private final SeckillActivityExpiryMapper expiry;
    private final SeckillInitialRuntimeRedisDAO runtime;
    private final TransactionTemplate write;
    private final SecurityFrameworkService security;
    public SeckillActivityExpiryService(SeckillActivityMapper activities,SeckillRedisEpochMapper epochs,SeckillPublishMapper publishes,
            SeckillPublishDecisionMapper decisions,SeckillAdmissionMapper admission,SeckillActivityExpiryMapper expiry,
            SeckillInitialRuntimeRedisDAO runtime,SecurityFrameworkService security,PlatformTransactionManager manager) {
        this.activities=activities;this.epochs=epochs;this.publishes=publishes;this.decisions=decisions;this.admission=admission;this.expiry=expiry;this.runtime=runtime;this.security=security;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public void expire(Long activityId) {
        Long tenant=context();if(!id(activityId))throw new IllegalArgumentException("需要原活动编号");
        SeckillRedisEpochDO stopped=stop(tenant,activityId,null,null,null);
        if(stopped==null)throw new IllegalStateException("原到期停止结果待确认");
        if(Integer.valueOf(40).equals(stopped.getStatus()) || "SYNCED".equals(stopped.getStopCacheState()))return;
        runtime.close(stopped); // DB已提交，失败只能保留关闭缓存待同步，不能说活动回滚。
        if(!Objects.equals(context(),tenant))throw new IllegalStateException("原到期停止缓存租户变化");
        Integer updated=write.execute(tx->expiry.cacheSynced(stopped));
        if(Integer.valueOf(1).equals(updated))return;
        SeckillRedisEpochDO current=epochs.initial(tenant,activityId,stopped.getGeneration());
        if(current!=null && Integer.valueOf(30).equals(current.getStatus()) && "SYNCED".equals(current.getStopCacheState())
                && Objects.equals(current.getInitToken(),stopped.getInitToken()) && Objects.equals(current.getDefinitionDigest(),stopped.getDefinitionDigest()))return;
        throw new IllegalStateException("原数据库已停止，Redis关闭进度仍待确认");
    }
    /** 平台命令只提交DB停止；Redis PENDING继续由原ACTIVITY_EXPIRE任务关闭，不在HTTP内调用Redis。 */
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermission('promotion:seckill-operation:query') && @ss.hasPermission('promotion:seckill-operation:stop')")
    public PlatformSeckillStopRespVO stopByPlatform(PlatformSeckillStopReqVO command) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("平台停止不能嵌套事务");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();platform(actor);
        if(!security.hasPermission("promotion:seckill-operation:query") || !security.hasPermission("promotion:seckill-operation:stop"))
            throw exception0(403,"没有平台秒杀停止权限");
        if(command==null || !id(command.getTenantId()) || !id(command.getId()) || command.getGeneration()==null || command.getGeneration()<1
                || command.getExpectedVersion()==null || command.getExpectedVersion()<0 || command.getExpectedVersion()>=9007199254740991L
                || command.getReason()==null || command.getReason().trim().isEmpty() || command.getReason().length()>500
                || command.getReason().chars().anyMatch(Character::isISOControl))throw exception0(400,"停止目标、原版本或原因不正确");
        String reason=command.getReason().trim();
        return TenantUtils.execute(command.getTenantId(),()->{
            SeckillRedisEpochDO stopped=stop(command.getTenantId(),command.getId(),command,actor,reason);
            if(stopped==null)throw new IllegalStateException("平台停止提交结果待确认，请按原目标版本重查");
            Long version=expiry.platformStopVersion(command.getTenantId(),command.getId(),command.getGeneration(),command.getExpectedVersion(),actor.getId(),reason);
            if(version==null || version!=command.getExpectedVersion()+1)throw new IllegalStateException("平台停止审计回执待确认");
            PlatformSeckillStopRespVO out=new PlatformSeckillStopRespVO();out.setTenantId(command.getTenantId());out.setId(command.getId());
            out.setGeneration(command.getGeneration());out.setStoppedVersion(version);
            out.setCacheCloseState(Integer.valueOf(40).equals(stopped.getStatus())?"NOT_REQUIRED":stopped.getStopCacheState());return out;
        });
    }
    private void platform(LoginUser actor) {
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))throw exception0(401,"平台登录已到期");
        if(!id(actor.getId()) || !UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || !Long.valueOf(0).equals(actor.getTenantId())
                || !Long.valueOf(0).equals(TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw exception0(403,"仅平台身份可以停止活动");
    }
    private SeckillRedisEpochDO stop(Long tenant,Long activityId,PlatformSeckillStopReqVO command,LoginUser actor,String reason) {
        return write.execute(tx->{
            if(!Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw new IllegalStateException("到期停止租户变化");
            if(command!=null && (SecurityFrameworkUtils.getLoginUser()!=actor || !Long.valueOf(0).equals(actor.getTenantId())
                    || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))))
                throw exception0(401,"平台停止身份发生变化或到期");
            SeckillActivityDO activity=activities.lock(tenant,activityId);
            if(activity==null || !Arrays.asList(0L,1L).contains(activity.getRedisEpoch()))throw new IllegalStateException("原活动不存在或epoch不支持");
            if(command!=null) {
                Long prior=expiry.platformStopVersion(tenant,activityId,command.getGeneration(),command.getExpectedVersion(),actor.getId(),reason);
                if(prior!=null) {
                    SeckillRedisEpochDO original=epochs.initial(tenant,activityId,command.getGeneration());
                    if(original==null || !Arrays.asList(30,40).contains(original.getStatus()))throw new IllegalStateException("原平台停止回执缺少关闭凭证");
                    return original;
                }
                if(!Objects.equals(activity.getGeneration(),command.getGeneration()) || !Objects.equals(activity.getVersion(),command.getExpectedVersion()))
                    throw exception0(409,"目标活动版本或轮次已变化，请按原命令查询");
            }
            SeckillRedisEpochDO epoch=epochs.initial(tenant,activityId,activity.getGeneration());
            if(epoch!=null && !Objects.equals(epoch.getPublishRequestId(),activity.getPublishOperationNo()))throw new IllegalStateException("原epoch发布号不一致");
            if(command==null && Integer.valueOf(30).equals(activity.getStatus()) && epoch!=null && Arrays.asList(30,40).contains(epoch.getStatus()))return epoch;
            if(!Integer.valueOf(20).equals(activity.getStatus())
                    || command==null && (activity.getEndTime()==null || activity.getEndTime().isAfter(admission.now())))throw new IllegalStateException("原活动尚未到期或运行阶段变化");
            SeckillPublishRecordDO original=publishes.get(tenant,activity.getPublishOperationNo());
            if(original==null || !Objects.equals(original.getActivityId(),activityId) || !Objects.equals(original.getGeneration(),activity.getGeneration())
                    || !Long.valueOf(1).equals(decisions.completed(tenant,activityId,"PUBLISH",activity.getPublishOperationNo()))
                    || !Long.valueOf(0).equals(decisions.completed(tenant,activityId,"WITHDRAW",activity.getPublishOperationNo())))
                throw new IllegalStateException("原发布事实不完整，保留义务待核查");
            if(epoch==null || Arrays.asList(0,10).contains(epoch.getStatus())) {
                if(epoch==null && !Long.valueOf(0).equals(activity.getRedisEpoch()) || epoch!=null && (!Long.valueOf(1).equals(activity.getRedisEpoch())
                        || epoch.getInitToken()!=null || epoch.getInitDeadline()!=null))throw new IllegalStateException("原初始化前状态不一致");
                String definition=JsonUtils.toJsonString(coldDefinition(activity,original));
                if(epoch==null) {
                    epoch=new SeckillRedisEpochDO();epoch.setTenantId(tenant);epoch.setActivityId(activityId);epoch.setGeneration(activity.getGeneration());
                    epoch.setEpoch(1L);epoch.setPublishRequestId(activity.getPublishOperationNo());epoch.setActorId(original.getActorId());
                    epoch.setFromVersion(activity.getVersion());epoch.setToVersion(activity.getVersion()+1);
                    epoch.setDefinitionSnapshot(definition);epoch.setDefinitionDigest(DigestUtil.sha256Hex(definition));
                    if(expiry.insertColdStopped(epoch)!=1)throw new IllegalStateException("原未运行关闭凭证尚未保存");
                } else {
                    if(!Objects.equals(epoch.getDefinitionDigest(),DigestUtil.sha256Hex(definition)) || expiry.closeBeforeRuntime(epoch)!=1)
                        throw new IllegalStateException("原未运行定义变化或关闭尚未保存");
                }
                if((command==null?expiry.stopColdActivity(tenant,activityId,activity.getVersion()):expiry.stopColdByPlatform(tenant,activityId,activity.getVersion(),"PLATFORM:"+actor.getId(),reason))!=1)throw new IllegalStateException("原未运行活动关闭尚未保存");
                epoch.setStatus(40);epoch.setStopCacheState("NOT_REQUIRED");
            } else {
                if(!Arrays.asList(15,20).contains(epoch.getStatus()) || epoch.getInitToken()==null || epoch.getInitDeadline()==null)
                    throw new IllegalStateException("原运行许可不完整，不能自动停止其他代次");
                if(epochs.markStopping(tenant,activityId,activity.getGeneration(),activity.getPublishOperationNo())!=1
                        || epochs.stopActivity(tenant,activityId,activity.getGeneration(),activity.getPublishOperationNo(),activity.getVersion(),command==null?"SYSTEM:EXPIRY":"PLATFORM:"+actor.getId(),command==null?"活动到期，系统停止新受理":reason)!=1)
                    throw new IllegalStateException("原活动到期停止尚未提交");
                epoch.setStatus(30);epoch.setStopCacheState("PENDING");
            }
            Map<String,Object> details=new LinkedHashMap<>();details.put("requestId",activity.getPublishOperationNo());details.put("epoch",epoch.getEpoch());
            details.put("source",command==null?"SYSTEM_EXPIRY":"PLATFORM");
            details.put("generation",activity.getGeneration());
            if(command!=null){details.put("actorRealm","platform");details.put("actorTenantId",0);details.put("reason",reason);}details.put("endTime",activity.getEndTime());
            details.put("runtimePermitIssued",epoch.getInitToken()!=null);
            if(activities.audit(tenant,activityId,command==null?0L:actor.getId(),command==null?"EPOCH_EXPIRE":"EPOCH_STOP",activity.getVersion(),activity.getVersion()+1,JsonUtils.toJsonString(details))!=1)
                throw new IllegalStateException("原系统到期审计未提交");
            return epoch;
        });
    }
    /** 原未运行池必须无受理/资金义务、全部实际划拨回执完整，再复用原发布定义构造方法。 */
    private SeckillPublishedManifest coldDefinition(SeckillActivityDO activity,SeckillPublishRecordDO original) {
        Long tenant=activity.getTenantId();
        if(!Long.valueOf(0).equals(expiry.coldObligations(tenant,activity.getId(),activity.getGeneration())))
            throw new IllegalStateException("未运行标记却有原受理义务，不能自动关闭");
        List<SeckillActivitySkuDO> skus=activities.skus(tenant,activity.getId());
        List<SeckillStockReceiptDO> receipts=decisions.latestAll(tenant,activity.getId(),activity.getGeneration());
        ProductSeckillAllocationReqDTO[] requests=JsonUtils.parseObject(original.getRequestSnapshot(),ProductSeckillAllocationReqDTO[].class);
        if(skus==null || skus.isEmpty() || skus.size()>200 || receipts==null || requests==null || receipts.size()!=skus.size() || requests.length!=skus.size())
            throw new IllegalStateException("原未运行规格或划拨回执不完整");
        Map<Long,ProductSeckillAllocationReqDTO> bySku=new HashMap<>();Set<String> operations=new HashSet<>();
        for(ProductSeckillAllocationReqDTO request:requests) {
            if(request==null || bySku.put(request.getSkuId(),request)!=null || !operations.add(request.getOperationNo()))throw new IllegalStateException("原冻结规格重复");
        }
        for(int i=0;i<skus.size();i++) {
            SeckillActivitySkuDO sku=skus.get(i);SeckillStockReceiptDO receipt=receipts.get(i);ProductSeckillAllocationReqDTO request=bySku.get(sku.getSkuId());
            if(request==null || !Objects.equals(receipt.getSkuId(),sku.getSkuId()) || !Integer.valueOf(20).equals(sku.getAllocationStatus())
                    || !Integer.valueOf(10).equals(receipt.getProductStatus()) || !Objects.equals(sku.getAllocationOperationNo(),request.getOperationNo())
                    || !Objects.equals(request.getActivityNo(),activity.getActivityNo()) || !Objects.equals(request.getGeneration(),activity.getGeneration())
                    || !Objects.equals(request.getSpuId(),activity.getProductSpuId()) || !Objects.equals(request.getSpuVersion(),activity.getProductSpuVersion())
                    || !Objects.equals(request.getSkuVersion(),sku.getSkuVersion()) || !Objects.equals(request.getQuantity(),sku.getPlannedQuantity())
                    || !Objects.equals(sku.getAllocatedQuantity(),request.getQuantity()) || !Objects.equals(sku.getAvailableQuantity(),sku.getAllocatedQuantity())
                    || !Objects.equals(receipt.getAllocatedQuantity(),sku.getAllocatedQuantity()) || !Integer.valueOf(0).equals(receipt.getReturnedQuantity())
                    || !Integer.valueOf(0).equals(sku.getHeldQuantity()) || !Integer.valueOf(0).equals(sku.getSoldQuantity())
                    || !Integer.valueOf(0).equals(sku.getReturnedQuantity()) || !Integer.valueOf(0).equals(sku.getReturnPendingQuantity()))
                throw new IllegalStateException("原未运行池不是完整未使用划拨，保留待核查");
        }
        SeckillConfigurationSnapshotDTO frozen=JsonUtils.parseObject(original.getConfigurationSnapshot(),SeckillConfigurationSnapshotDTO.class);
        return SeckillPublishDecisionService.definition(activity,skus,original,frozen);
    }
    public Result expireBatch(int limit,Long after,String trace) {
        Long tenant=context();if(limit<1 || limit>5 || after!=null && !id(after) || !uuid(trace))throw new IllegalArgumentException("到期停止批次或游标不正确");
        List<SeckillActivityDO> rows=expiry.candidates(tenant,after,limit);
        if(rows==null || rows.size()>limit)throw new IllegalStateException("到期活动批次不完整");
        Result out=new Result();if(!rows.isEmpty())out.nextActivity=after;long previous=after==null?0:after,started=System.nanoTime();
        for(SeckillActivityDO row:rows) {
            if(row==null || !Objects.equals(row.getTenantId(),tenant) || !id(row.getId()) || row.getId()<=previous)throw new IllegalStateException("到期活动归属或顺序不一致");
            if(System.nanoTime()-started>=45000000000L)break;out.attempted++;
            try {expire(row.getId());out.synchronizedCount++;}
            catch(RuntimeException pending) {out.pending++;log.warn("Seckill expiry pending tenant={},activity={},trace={}",tenant,row.getId(),trace);}
            previous=row.getId();out.nextActivity=row.getId();
        }
        return out;
    }
    private Long context() {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("到期停止仅允许事务外可信后台");
        Long tenant=TenantContextHolder.getTenantId();if(!id(tenant) || TenantContextHolder.isIgnore())throw new IllegalStateException("需要可信当前租户");return tenant;
    }
    private boolean id(Long x){return x!=null && x>0 && x<=9007199254740991L;}
    private boolean uuid(String x){return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");}
    @Data public static class Result {private int attempted;private int synchronizedCount;private int pending;private Long nextActivity;}
}
