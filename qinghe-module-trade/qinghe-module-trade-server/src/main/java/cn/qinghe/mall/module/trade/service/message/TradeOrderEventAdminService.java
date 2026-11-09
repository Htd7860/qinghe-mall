package cn.qinghe.mall.module.trade.service.message;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderOutboxDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderEventAdminMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Map;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原通知任务分页/详情委托，复用现有订单恢复管理权限及原请求幂等处理。
 * 重放只登记原事件新投递代次；不调用Broker、不重置业务任务或退款授权。
 * @author 芋道源码
 */
@Service
public class TradeOrderEventAdminService {
    private final TradeOrderEventAdminMapper records;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public TradeOrderEventAdminService(TradeOrderEventAdminMapper records,SecurityFrameworkService security,
            Validator validator,PlatformTransactionManager manager) {
        this.records=records;this.security=security;this.validator=validator;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }

    public PageResult<TradeEventRespVO> page(TradeEventPageReqVO request) {
        LoginUser actor=authorize("query");long offset=offset(request);
        return read.execute(tx -> new PageResult<>(records.count(actor.getTenantId(),request),records.page(actor.getTenantId(),request,offset)));
    }
    public TradeEventRespVO get(String eventId) {
        LoginUser actor=authorize("query");uuid(eventId);return required(actor.getTenantId(),eventId);
    }
    public TradeEventReplayRespVO replayResult(String requestId) {
        LoginUser actor=authorize("query");uuid(requestId);
        TradeEventReplayRespVO row=records.replay(actor.getTenantId(),requestId);
        if(row==null)throw exception0(404,"本租户原事件重放回执不存在");return row;
    }
    public PageResult<TradeEventReplayRespVO> history(String eventId,PageParam request) {
        LoginUser actor=authorize("query");uuid(eventId);long offset=offset(request);
        return read.execute(tx -> {
            required(actor.getTenantId(),eventId);
            return new PageResult<>(records.historyCount(actor.getTenantId(),eventId),
                    records.history(actor.getTenantId(),eventId,request.getPageSize(),offset));
        });
    }

    public TradeEventReplayRespVO replay(TradeEventReplayReqVO request) {
        long checkedAt=System.nanoTime();LoginUser actor=authorize("replay");
        if(request==null || !validator.validate(request).isEmpty())throw exception0(400,"事件重放参数不正确");
        String reason=request.getReason().trim();
        if(reason.isEmpty() || reason.chars().anyMatch(Character::isISOControl))throw exception0(400,"请填写有效重放原因");
        TradeEventRespVO reference=required(actor.getTenantId(),request.getEventId());
        if(reference.getEventType().startsWith("AFTERSALE_") && !security.hasPermission("trade:after-sale:refund"))
            throw exception0(403,"售后事件重放还需要资金退款权限");
        try {
            return write.execute(tx -> {
                fresh(actor,checkedAt);
                TradeOrderOutboxDO current=records.lock(actor.getTenantId(),request.getEventId());
                if(current==null)throw exception0(404,"本租户原事件不存在");
                if(!Objects.equals(current.getEventType(),reference.getEventType()))throw exception0(409,"原事件类型已变化");
                TradeEventReplayRespVO old=records.replay(actor.getTenantId(),request.getRequestId());
                if(old!=null){same(old,actor,request,reason);fresh(actor,checkedAt);return old;}
                if(!Objects.equals(current.getReplayVersion(),request.getExpectedVersion())
                        || current.getLeaseOwner()!=null || current.getLeaseUntil()!=null
                        || !("MANUAL".equals(current.getStatus()) || "PUBLISHED".equals(current.getStatus())))
                    throw exception0(409,"仅当前代次的人工或已发布事件允许重放");
                if(records.consumed(actor.getTenantId(),current.getEventId())!=0)
                    throw exception0(409,"原事件已消费，请查询对应业务恢复任务");
                // 不允许修改原事件内容来绕过核对；损坏记录留待核查，不制造新事件身份。
                String canonical=TradeOrderEventEnvelope.encode(current,actor.getTenantId());
                LocalDateTime instant=now();
                if("PUBLISHED".equals(current.getStatus()) && "ORDER_EXPIRE_CHECK".equals(current.getEventType())) {
                    long due=((Number)JsonUtils.parseObject(canonical,Map.class).get("dueAt")).longValue();
                    if(System.currentTimeMillis()<Math.addExact(due,60_000L))throw exception0(409,"原到期事件仍在正常延迟窗口，请稍后核对");
                }
                if("PUBLISHED".equals(current.getStatus()) && (current.getPublishedAt()==null
                        || current.getPublishedAt().isAfter(instant.minusMinutes(1))))throw exception0(409,"原事件发布尚未满一分钟，请稍后核对消费进度");
                TradeEventReplayRespVO audit=new TradeEventReplayRespVO();
                audit.setRequestId(request.getRequestId());audit.setEventId(current.getEventId());audit.setActorId(actor.getId());audit.setReason(reason);
                audit.setFromVersion(current.getReplayVersion());audit.setToVersion(Math.addExact(current.getReplayVersion(),1));
                audit.setPreviousStatus(current.getStatus());audit.setPreviousAttempts(current.getAttempts());
                audit.setPreviousErrorCode(current.getLastErrorCode());audit.setPreviousPublishedAt(current.getPublishedAt());audit.setCreateTime(instant);
                if(records.requeue(actor.getTenantId(),current.getEventId(),current.getReplayVersion(),instant)!=1)
                    throw exception0(409,"原事件投递状态已变化，请重新读取");
                if(records.insertReplay(actor.getTenantId(),audit)!=1)throw new IllegalStateException("原事件重放记录保存失败");
                fresh(actor,checkedAt);return audit;
            });
        } catch(DuplicateKeyException conflict) {
            TradeEventReplayRespVO old=records.replay(actor.getTenantId(),request.getRequestId());
            if(old==null)throw exception0(409,"原事件已由其他操作重放，请重新读取");
            same(old,actor,request,reason);fresh(actor,checkedAt);return old;
        }
    }

    private LoginUser authorize(String permission) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("事件管理权限检查不能放在外层事务内");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0
                || actor.getTenantId()>9_007_199_254_740_991L || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L
                || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))
            throw exception0(403,"事件管理仅限当前商家员工");
        if(!security.hasPermission("trade:order-recovery:"+permission))throw exception0(403,"没有订单恢复操作权限");
        return actor;
    }
    private void fresh(LoginUser actor,long checkedAt) {
        LoginUser current=SecurityFrameworkUtils.getLoginUser();
        if(current==null || !Objects.equals(current.getId(),actor.getId()) || !Objects.equals(current.getTenantId(),actor.getTenantId())
                || !Objects.equals(current.getUserType(),actor.getUserType()) || current.getExpiresTime()==null
                || !current.getExpiresTime().isAfter(LocalDateTime.now()) || TenantContextHolder.isIgnore()
                || !Objects.equals(TenantContextHolder.getTenantId(),actor.getTenantId()))throw exception0(403,"当前员工身份已变化");
        if(System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"权限核对已过时，请查询原事件重放回执");
    }
    private TradeEventRespVO required(Long tenant,String event) {
        TradeEventRespVO row=records.get(tenant,event);
        if(row==null)throw exception0(404,"本租户原事件不存在");return row;
    }
    private void same(TradeEventReplayRespVO row,LoginUser actor,TradeEventReplayReqVO request,String reason) {
        if(!Objects.equals(row.getEventId(),request.getEventId()) || !Objects.equals(row.getActorId(),actor.getId())
                || !Objects.equals(row.getFromVersion(),request.getExpectedVersion()) || !Objects.equals(row.getReason(),reason))
            throw exception0(409,"原重放请求号不能更换事件、操作人、代次或原因");
    }
    private long offset(PageParam request) {
        if(request==null || !validator.validate(request).isEmpty() || request.getPageSize()>100)throw exception0(400,"分页参数不正确");
        long value=((long)request.getPageNo()-1)*request.getPageSize();
        if(value>100_000)throw exception0(400,"分页范围过大，请缩小查询范围");return value;
    }
    private void uuid(String id) {
        if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw exception0(400,"事件或请求编号格式不正确");
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
