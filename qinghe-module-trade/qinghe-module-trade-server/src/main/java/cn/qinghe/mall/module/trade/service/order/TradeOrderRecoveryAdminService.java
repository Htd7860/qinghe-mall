package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原订单Controller/Service的权限→当前租户查询/条件写职责；不建立独立运维平台。
 * 人工操作仅重新排队原MANUAL动作，不能改业务身份或直接触发资金/库存修改。
 * @author 芋道源码
 */
@Service
public class TradeOrderRecoveryAdminService {
    private final TradeOrderRecoveryMapper records;
    private final TradeOrderRecoveryReplayMapper replays;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    public TradeOrderRecoveryAdminService(TradeOrderRecoveryMapper records,TradeOrderRecoveryReplayMapper replays,
            SecurityFrameworkService security,Validator validator,PlatformTransactionManager manager) {
        this.records=records;this.replays=replays;this.security=security;this.validator=validator;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public PageResult<TradeRecoveryRespVO> page(TradeRecoveryPageReqVO request) {
        LoginUser actor=authorize("query");long offset=pageOffset(request);
        return read.execute(status -> new PageResult<>(records.countPage(actor.getTenantId(),request.getStatus(),request.getKind()),
                BeanUtils.toBean(records.page(actor.getTenantId(),request.getStatus(),request.getKind(),request.getPageSize(),offset),TradeRecoveryRespVO.class)));
    }
    public TradeRecoveryRespVO get(Long id) {
        LoginUser actor=authorize("query");positive(id);
        return BeanUtils.toBean(required(actor.getTenantId(),id,false),TradeRecoveryRespVO.class);
    }
    public TradeRecoveryReplayRespVO replayResult(String requestId) {
        LoginUser actor=authorize("query");uuid(requestId);
        TradeOrderRecoveryReplayDO row=replays.find(actor.getTenantId(),requestId);
        if(row==null)throw exception0(404,"本租户重放回执不存在");return receipt(row);
    }
    public PageResult<TradeRecoveryReplayRespVO> history(Long id,PageParam request) {
        LoginUser actor=authorize("query");positive(id);long offset=pageOffset(request);
        return read.execute(status -> {
            required(actor.getTenantId(),id,false);
            return new PageResult<>(replays.countHistory(actor.getTenantId(),id),
                    BeanUtils.toBean(replays.history(actor.getTenantId(),id,request.getPageSize(),offset),TradeRecoveryReplayRespVO.class));
        });
    }

    public TradeRecoveryReplayRespVO replay(TradeRecoveryReplayReqVO request) {
        long checkedAt=System.nanoTime();LoginUser actor=authorize("replay");
        if(request==null || !validator.validate(request).isEmpty())throw exception0(400,"重放参数不正确");
        String reason=request.getReason().trim();
        if(reason.isEmpty() || reason.chars().anyMatch(Character::isISOControl))throw exception0(400,"请填写有效重放原因");
        TradeOrderRecoveryDO reference=required(actor.getTenantId(),request.getId(),false);
        if ("REFUND".equals(reference.getKind()) && !security.hasPermission("trade:after-sale:refund"))
            throw exception0(403,"原退款恢复还需要资金退款权限");
        try {
            return write.execute(status -> {
                fresh(actor,checkedAt);
                TradeOrderRecoveryDO current=required(actor.getTenantId(),request.getId(),true);
                if (!Objects.equals(current.getKind(),reference.getKind()) || !Objects.equals(current.getOrderId(),reference.getOrderId())
                        || !Objects.equals(current.getAfterSaleId(),reference.getAfterSaleId())) throw exception0(409,"原恢复目标已变化");
                TradeOrderRecoveryReplayDO old=replays.find(actor.getTenantId(),request.getRequestId());
                if(old!=null){same(old,actor,request,reason);fresh(actor,checkedAt);return receipt(old);}
                if(!"MANUAL".equals(current.getStatus()) || !Objects.equals(current.getAuthVersion(),request.getExpectedVersion())
                        || current.getLeaseOwner()!=null || current.getLeaseUntil()!=null)throw exception0(409,"仅当前版本的人工记录可以重新排队");
                LocalDateTime instant=now();
                if(records.requeue(actor.getTenantId(),current.getId(),current.getAuthVersion(),request.getRequestId(),instant)!=1)
                    throw exception0(409,"恢复记录已变化，请重新读取");
                TradeOrderRecoveryReplayDO audit=new TradeOrderRecoveryReplayDO();
                audit.setTenantId(actor.getTenantId());audit.setRecoveryId(current.getId());audit.setOrderId(current.getOrderId());audit.setKind(current.getKind());
                audit.setRequestId(request.getRequestId());audit.setActorId(actor.getId());audit.setReason(reason);
                audit.setFromVersion(current.getAuthVersion());audit.setToVersion(Math.addExact(current.getAuthVersion(),1));
                audit.setPreviousAttempts(current.getAttempts());audit.setPreviousErrorCode(current.getLastErrorCode());
                audit.setStatus("READY");audit.setAttempts(0);audit.setLastVersion(audit.getToVersion());audit.setCreateTime(instant);audit.setUpdateTime(instant);
                if(replays.insert(audit)!=1)throw new IllegalStateException("重放依据审计保存失败");
                fresh(actor,checkedAt);return receipt(audit);
            });
        } catch(DuplicateKeyException conflict) {
            // 不同记录误用同一请求号时，当前事务整体回滚；只认已提交的同人同参回执。
            TradeOrderRecoveryReplayDO old=replays.find(actor.getTenantId(),request.getRequestId());
            if(old==null)throw exception0(409,"原记录已由其他操作重放，请重新读取");
            same(old,actor,request,reason);fresh(actor,checkedAt);return receipt(old);
        }
    }
    private LoginUser authorize(String permission) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("恢复管理权限检查不能放在外层事务内");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0
                || actor.getId()==null || actor.getId()<=0 || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"恢复管理仅限当前商家员工");
        if(!security.hasPermission("trade:order-recovery:"+permission))throw exception0(403,"没有订单恢复操作权限");
        // 清理既有义务不检查canPurchase，不因停止新经营而阻止恢复；身份/权限仍实时复核。
        return actor;
    }
    private void fresh(LoginUser actor,long checkedAt) {
        LoginUser current=SecurityFrameworkUtils.getLoginUser();
        if(current==null || !Objects.equals(current.getId(),actor.getId()) || !Objects.equals(current.getTenantId(),actor.getTenantId())
                || current.getExpiresTime()==null || !current.getExpiresTime().isAfter(LocalDateTime.now())
                || !Objects.equals(TenantContextHolder.getTenantId(),actor.getTenantId()) || TenantContextHolder.isIgnore())throw exception0(403,"当前员工身份已变化");
        if(System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"权限核对已过时，请查询原重放回执");
    }
    private TradeOrderRecoveryDO required(Long tenant,Long id,boolean lock) {
        TradeOrderRecoveryDO row=lock?records.lock(tenant,id):records.get(tenant,id);
        if(row==null)throw exception0(404,"本租户恢复记录不存在");return row;
    }
    private void same(TradeOrderRecoveryReplayDO row,LoginUser actor,TradeRecoveryReplayReqVO request,String reason) {
        if(!Objects.equals(row.getRecoveryId(),request.getId()) || !Objects.equals(row.getFromVersion(),request.getExpectedVersion())
                || !Objects.equals(row.getActorId(),actor.getId()) || !Objects.equals(row.getReason(),reason))throw exception0(409,"原重放请求号不能更换记录、版本、操作人或原因");
    }
    private long pageOffset(PageParam request) {
        if(request==null || !validator.validate(request).isEmpty() || request.getPageSize()>100)throw exception0(400,"分页参数不正确");
        long offset=((long)request.getPageNo()-1)*request.getPageSize();
        if(offset>100_000)throw exception0(400,"分页范围过大，请缩小状态或动作范围");return offset;
    }
    private void positive(Long id){if(id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(400,"恢复记录编号不正确");}
    private void uuid(String id){if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw exception0(400,"重放请求号格式不正确");}
    private TradeRecoveryReplayRespVO receipt(TradeOrderRecoveryReplayDO row){return BeanUtils.toBean(row,TradeRecoveryReplayRespVO.class);}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
