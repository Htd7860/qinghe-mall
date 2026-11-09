package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderFulfillmentMapper;
import cn.qinghe.mall.module.trade.dal.mysql.delivery.DeliveryExpressMapper;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.service.aftersale.dto.*;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import lombok.extern.slf4j.Slf4j;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原审核/撤销/寄回/验收状态转换职责。审核、验收不执行资金或恢复库存，释放不删除历史。
 * 原订单父锁→售后→单位；未来退款开始亦须此顺序并先转换不可撤销状态再调用渠道。
 * @author 芋道源码
 */
@Service
@Slf4j
public class AfterSaleDecisionService {
    private final AfterSaleDecisionMapper decisions;
    private final AfterSaleMapper sales;
    private final TradeOrderFulfillmentMapper orders;
    private final TradeMemberAccess members;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final TransactionTemplate write;
    private final DeliveryExpressMapper carriers;
    public AfterSaleDecisionService(AfterSaleDecisionMapper decisions,AfterSaleMapper sales,TradeOrderFulfillmentMapper orders,
            TradeMemberAccess members,SecurityFrameworkService security,Validator validator,DeliveryExpressMapper carriers,PlatformTransactionManager manager) {
        this.decisions=decisions;this.sales=sales;this.orders=orders;this.members=members;this.security=security;this.validator=validator;this.carriers=carriers;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public AfterSaleDecisionResult approve(AfterSaleDecisionCommand body) {
        validate(body);outside();LoginUser actor=merchant();return decide(actor,false,"ADMIN_APPROVE",body,System.nanoTime());
    }
    public AfterSaleDecisionResult reject(AfterSaleDecisionCommand body) {
        validate(body);outside();LoginUser actor=merchant();return decide(actor,false,"ADMIN_REJECT",body,System.nanoTime());
    }
    public AfterSaleDecisionResult cancel(HttpServletRequest request,String store,AfterSaleDecisionCommand body) {
        validate(body);outside();members.check(request,store);LoginUser actor=members.actor();
        return decide(actor,true,"MEMBER_CANCEL",body,System.nanoTime());
    }
    public AfterSaleDecisionResult deliver(HttpServletRequest request,String store,AfterSaleReturnDeliveryCommand body) {
        validate(body);outside();
        if(!"买家提交退货物流".equals(body.getReason()))throw exception0(400,"退货物流命令不正确");
        members.check(request,store);LoginUser actor=members.actor();return decide(actor,true,"MEMBER_DELIVERY",body,System.nanoTime());
    }
    /** 复用原收货/拒收事务职责；拒收保持争议，不终止实物及退款义务。 */
    public AfterSaleDecisionResult inspect(AfterSaleInspectionCommand body) {
        validate(body);outside();LoginUser actor=localMerchant();
        if(!security.hasPermission("trade:after-sale:receive"))throw exception0(403,"没有售后验货权限");
        return decide(actor,false,body.getAccepted()?"ADMIN_RECEIVE":"ADMIN_RECEIVE_DISPUTE",body,System.nanoTime());
    }
    /** 可信调度按原订单逐笔关闭；false表示本单仍有到期申请，复用恢复行继续推进。 */
    public boolean expireReturnByOrder(Long orderId) {
        outside();Long tenant=systemTenant();
        if(orderId==null || orderId<=0 || orderId>9_007_199_254_740_991L)throw exception0(400,"原订单编号不正确");
        return Boolean.TRUE.equals(write.execute(status -> {
            TradeOrderDO order=orders.lock(tenant,orderId);
            if(order==null)throw exception0(409,"原订单缺失，不能释放售后占用");
            LocalDateTime instant=now();AfterSaleDO row=decisions.nextExpired(tenant,orderId,instant);
            if(row==null)return true;
            if(!Objects.equals(row.getUserId(),order.getUserId()))throw exception0(503,"售后与原订单归属需先核对");
            int pending=sales.pending(tenant,orderId);
            if(pending<1 || !Integer.valueOf(pending).equals(order.getAfterSalePendingCount()))throw exception0(503,"订单售后计数需先核对");
            AfterSaleRefundCalculator.verifyReservedUnits(row,decisions.units(tenant,row.getId()));
            if(decisions.expire(tenant,row.getId(),row.getAuthVersion(),instant)!=1)throw exception0(409,"寄回状态或期限已变化");
            if(decisions.release(tenant,row.getId())!=row.getCount())throw new IllegalStateException("超期售后原商品单位释放不完整");
            if(decisions.parent(tenant,orderId,order.getAuthVersion(),1,instant)!=1)throw exception0(409,"原订单版本或阶段已变化");
            long next=Math.addExact(row.getAuthVersion(),1),parentNext=Math.addExact(order.getAuthVersion(),1);
            if(decisions.log(tenant,row.getId(),0L,"SYSTEM","SYSTEM_RETURN_EXPIRE",20,64,next,instant,null,"买家寄回超期未提交物流",false,null)!=1
                    || orders.log(tenant,orderId,0L,"SYSTEM","AFTERSALE_EXPIRE",order.getStatus(),order.getStatus(),parentNext,null,instant)!=1)
                throw new IllegalStateException("超期关闭日志保存失败");
            recordEvent(tenant,orderId,row.getId(),64,parentNext,instant);
            if(!Objects.equals(tenant,systemTenant()))throw exception0(403,"任务租户已变化");
            return decisions.nextExpired(tenant,orderId,instant)==null;
        }));
    }
    /** 每轮调度有界输出48小时超时提醒；提醒不批准、拒绝、退款或释放任何申请。 */
    public void warnOverdue() {
        outside();Long tenant=systemTenant();List<AfterSaleDO> overdue=decisions.overdue(tenant,now());
        for(int i=0;i<Math.min(20,overdue.size());i++) {
            AfterSaleDO row=overdue.get(i);
            log.warn("售后处理超过48小时 tenant={}, order={}, afterSale={}, status={}, trace={}",tenant,row.getOrderId(),row.getId(),row.getStatus(),MDC.get("traceId"));
        }
        if(overdue.size()>20)log.warn("售后超时提醒超过单轮20条 tenant={}, trace={}，需分页查询处理",tenant,MDC.get("traceId"));
    }
    private AfterSaleDecisionResult decide(LoginUser actor,boolean member,String action,AfterSaleDecisionCommand body,long checkedAt) {
        String reason=body.getReason().trim();boolean quality=Boolean.TRUE.equals(body.getQualityApproved());
        Map<String,Object> detail=commandDetails(body,action);String details=detail==null?null:JsonUtils.toJsonString(detail);
        boolean shipping="MEMBER_DELIVERY".equals(action);
        boolean inspection="ADMIN_RECEIVE".equals(action)||"ADMIN_RECEIVE_DISPUTE".equals(action);
        if(!"ADMIN_APPROVE".equals(action)&&quality)throw exception0(400,"仅同意审核可确认质量问题");
        AfterSaleDO reference=decisions.get(actor.getTenantId(),body.getId());
        if(reference==null || member&&!Objects.equals(reference.getUserId(),actor.getId()))throw exception0(404,"本入口售后不存在");
        try {
            return write.execute(status -> {
                fresh(actor,member,checkedAt);
                TradeOrderDO order=orders.lock(actor.getTenantId(),reference.getOrderId());fresh(actor,member,checkedAt);
                AfterSaleDO row=decisions.lock(actor.getTenantId(),body.getId());
                if(order==null || row==null || !Objects.equals(row.getOrderId(),order.getId()) || !Objects.equals(row.getUserId(),order.getUserId())
                        || member&&!Objects.equals(row.getUserId(),actor.getId()))throw exception0(404,"本入口售后不存在");
                AfterSaleLogDO old=decisions.receipt(actor.getTenantId(),body.getRequestId());
                if(old!=null)return same(old,actor,member,action,body,reason,quality,detail);
                if(!Objects.equals(row.getAuthVersion(),body.getExpectedVersion()))throw exception0(409,"售后版本已变化，请查询原申请");
                boolean audit=!member&&!inspection;
                boolean eligible=inspection?Arrays.asList(30,35).contains(row.getStatus()):audit?row.getStatus()==10:
                        shipping?row.getStatus()==20:Arrays.asList(10,20,40).contains(row.getStatus())
                        && row.getDeliveryTime()==null && row.getReceiveTime()==null && (row.getStatus()!=40 || row.getWay()==10);
                if(!eligible)
                    throw exception0(409,"当前阶段不能执行此决定；退款处理中或已寄回商品须继续核查");
                int pending=sales.pending(actor.getTenantId(),order.getId());
                if(pending<1 || !Integer.valueOf(pending).equals(order.getAfterSalePendingCount()))throw exception0(503,"订单售后计数需先核对");
                AfterSaleRefundCalculator.verifyReservedUnits(row,decisions.units(actor.getTenantId(),row.getId()));
                boolean release=member&&!shipping || "ADMIN_REJECT".equals(action);
                int to=inspection?("ADMIN_RECEIVE".equals(action)?40:35):shipping?30:member?61:release?62:row.getWay()==10?40:20;
                LocalDateTime instant=now(),deadline=to==20?instant.plusDays(7):null;
                if(to==20 && details==null || to!=20 && !shipping && body.getReturnAddress()!=null)throw exception0(400,"仅同意退货退款需要提供本次寄回地址");
                int changed;
                if(shipping) {
                    if(row.getReturnAddress()==null || row.getReturnDeadline()==null || !instant.isBefore(row.getReturnDeadline()))throw exception0(409,"寄回期限已到或原寄回资料不完整，请查询原申请");
                    AfterSaleReturnDeliveryCommand parcel=(AfterSaleReturnDeliveryCommand)body;
                    DeliveryExpressDO carrier=carriers.lockEnabled(parcel.getLogisticsId());if(carrier==null)throw exception0(409,"承运商不存在或已停用");
                    changed=decisions.deliver(actor.getTenantId(),row.getId(),row.getAuthVersion(),carrier.getId(),carrier.getCode(),carrier.getName(),parcel.getLogisticsNo(),instant);
                } else if(inspection) {
                    AfterSaleInspectionCommand receipt=(AfterSaleInspectionCommand)body;
                    if(row.getWay()!=20 || row.getDeliveryTime()==null || receipt.getReceivedCount()<row.getReceivedCount()
                            || to==40&&!Objects.equals(receipt.getReceivedCount(),row.getCount()))
                        throw exception0(409,"验收数量须核对原申请；已收到实物不能撤销记录，数量不符请保留待核查");
                    changed=decisions.inspect(actor.getTenantId(),row.getId(),row.getStatus(),to,row.getAuthVersion(),actor.getId(),
                            receipt.getReceivedCount(),receipt.getSalableCount(),reason,instant);
                } else changed=decisions.transition(actor.getTenantId(),row.getId(),row.getStatus(),to,row.getAuthVersion(),audit,actor.getId(),reason,quality,deadline,details,instant);
                if(changed!=1)
                    throw exception0(409,"售后状态已变化，请查询原回执");
                if(release && decisions.release(actor.getTenantId(),row.getId())!=row.getCount())throw new IllegalStateException("原商品单位释放不完整");
                if(decisions.parent(actor.getTenantId(),order.getId(),order.getAuthVersion(),release?1:0,instant)!=1)throw exception0(409,"订单版本已变化");
                long next=Math.addExact(row.getAuthVersion(),1),parentNext=Math.addExact(order.getAuthVersion(),1);
                String actorType=member?"MEMBER":"ADMIN",parentAction=inspection?"AFTERSALE_INSPECT":shipping?"AFTERSALE_DELIVERY":member?"AFTERSALE_CANCEL":release?"AFTERSALE_REJECT":"AFTERSALE_APPROVE";
                if(decisions.log(actor.getTenantId(),row.getId(),actor.getId(),actorType,action,row.getStatus(),to,next,instant,body.getRequestId(),reason,quality,details)!=1
                        || orders.log(actor.getTenantId(),order.getId(),actor.getId(),actorType,parentAction,order.getStatus(),order.getStatus(),parentNext,null,instant)!=1)
                    throw new IllegalStateException("售后决定日志保存失败");
                recordEvent(actor.getTenantId(),order.getId(),row.getId(),to,parentNext,instant);
                fresh(actor,member,checkedAt);return new AfterSaleDecisionResult(row.getId(),to,next);
            });
        } catch(DuplicateKeyException conflict) {
            fresh(actor,member,checkedAt);AfterSaleLogDO old=decisions.receipt(actor.getTenantId(),body.getRequestId());
            if(old==null)throw exception0(409,"决定回执或版本已变化，请查询原申请");
            return same(old,actor,member,action,body,reason,quality,detail);
        }
    }
    private void recordEvent(Long tenant,Long orderId,Long afterSaleId,int to,long version,LocalDateTime instant) {
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("orderId",orderId);payload.put("afterSaleId",afterSaleId);payload.put("status",to);
        String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();payload.put("traceId",trace);
        if(orders.event(UUID.randomUUID().toString(),tenant,orderId,"AFTERSALE_DECIDED",version,JsonUtils.toJsonString(payload),instant)!=1)
            throw new IllegalStateException("售后待发送决定事件保存失败");
    }
    private Long systemTenant() {
        Long tenant=TenantContextHolder.getTenantId();
        if(SecurityContextHolder.getContext().getAuthentication()!=null || RequestContextHolder.getRequestAttributes()!=null
                || tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore())
            throw exception0(403,"超期关闭仅允许可信租户后台调度");
        return tenant;
    }
    private AfterSaleDecisionResult same(AfterSaleLogDO old,LoginUser actor,boolean member,String action,AfterSaleDecisionCommand body,String reason,boolean quality,Map<String,Object> details) {
        if(!Objects.equals(old.getAfterSaleId(),body.getId()) || !Objects.equals(old.getActorId(),actor.getId()) || !Objects.equals(old.getActorType(),member?"MEMBER":"ADMIN")
                || !Objects.equals(old.getAction(),action) || !Objects.equals(old.getAfterSaleVersion(),Math.addExact(body.getExpectedVersion(),1))
                || !Objects.equals(old.getReason(),reason) || !Objects.equals(old.getQualityApproved(),quality)
                || !Objects.equals(old.getDetails()==null?null:JsonUtils.parseObject(old.getDetails(),Map.class),
                    details==null?null:JsonUtils.parseObject(JsonUtils.toJsonString(details),Map.class)))throw exception0(409,"同一决定号内容不同，请查询原回执");
        return new AfterSaleDecisionResult(old.getAfterSaleId(),old.getToStatus(),old.getAfterSaleVersion());
    }
    private LoginUser merchant() {
        LoginUser actor=localMerchant();if(!security.hasPermission("trade:after-sale:audit"))throw exception0(403,"没有售后审核权限");return actor;
    }
    private LoginUser localMerchant() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L
                || actor.getTenantId()==null || actor.getTenantId()<=0 || actor.getTenantId()>9_007_199_254_740_991L
                || TenantContextHolder.isIgnore() || !Objects.equals(TenantContextHolder.getTenantId(),actor.getTenantId()))throw exception0(403,"仅当前商家员工可审核售后");return actor;
    }
    private void fresh(LoginUser actor,boolean member,long checkedAt) {
        LoginUser current=member?members.actor():localMerchant();
        if(!Objects.equals(current.getId(),actor.getId()) || !Objects.equals(current.getTenantId(),actor.getTenantId()) || System.nanoTime()-checkedAt>5_000_000_000L)
            throw exception0(503,"身份核对已过时，请查询原决定");
    }
    private void validate(AfterSaleDecisionCommand body) {
        if(body==null || !validator.validate(body).isEmpty() || body.getReason().trim().isEmpty() || body.getReason().chars().anyMatch(Character::isISOControl))throw exception0(400,"请填写有效售后决定及原因");
    }
    private Map<String,Object> commandDetails(AfterSaleDecisionCommand body,String action) {
        if("ADMIN_RECEIVE".equals(action)||"ADMIN_RECEIVE_DISPUTE".equals(action)) {
            if(!(body instanceof AfterSaleInspectionCommand) || body.getReturnAddress()!=null)throw exception0(400,"验货命令不正确");
            AfterSaleInspectionCommand receipt=(AfterSaleInspectionCommand)body;
            if(receipt.getSalableCount()>receipt.getReceivedCount())throw exception0(400,"可售数量不能超过实收数量");
            Map<String,Object> map=new LinkedHashMap<>();map.put("receivedCount",receipt.getReceivedCount());map.put("salableCount",receipt.getSalableCount());
            map.put("unsalableCount",receipt.getReceivedCount()-receipt.getSalableCount());map.put("accepted",receipt.getAccepted());return map;
        }
        if("MEMBER_DELIVERY".equals(action)) {
            if(body.getReturnAddress()!=null || !(body instanceof AfterSaleReturnDeliveryCommand))throw exception0(400,"寄回物流命令不正确");
            AfterSaleReturnDeliveryCommand parcel=(AfterSaleReturnDeliveryCommand)body;
            Map<String,Object> map=new LinkedHashMap<>();map.put("logisticsId",parcel.getLogisticsId());map.put("logisticsNo",parcel.getLogisticsNo());return map;
        }
        AfterSaleReturnAddress address=body.getReturnAddress();if(address==null)return null;
        if(!"ADMIN_APPROVE".equals(action) || !AreaUtils.isChinaRegion(address.getAreaId()) || address.getName().trim().isEmpty() || address.getDetail().trim().isEmpty()
                || address.getName().chars().anyMatch(Character::isISOControl) || address.getDetail().chars().anyMatch(Character::isISOControl))throw exception0(400,"本次寄回地址不正确");
        Map<String,Object> map=new LinkedHashMap<>();map.put("name",address.getName().trim());map.put("mobile",address.getMobile());map.put("areaId",address.getAreaId());map.put("detail",address.getDetail().trim());return map;
    }
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("售后决定入口不能在外层事务内");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
