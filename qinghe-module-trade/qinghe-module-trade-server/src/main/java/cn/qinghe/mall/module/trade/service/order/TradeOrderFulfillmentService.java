package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeOrderLogisticsUpdateReqVO;
import cn.qinghe.mall.module.trade.controller.app.order.vo.AppTradeOrderReceiveReqVO;
import cn.qinghe.mall.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.qinghe.mall.module.trade.dal.mysql.delivery.DeliveryExpressMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderFulfillmentMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOrderDeliveryLogDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.slf4j.MDC;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原deliveryOrder/receiveOrder0状态更新及日志职责；当前一次快递发货。
 * 订单父锁是后续售后占用、退款数量与履约的共同锁。只更新本地事实，不再扣库存或执行资金操作。
 * 消费者收货/商家履约HTTP与可信恢复任务均委托本类。
 * @author 芋道源码
 */
@Service
public class TradeOrderFulfillmentService {
    private final TradeOrderFulfillmentMapper orders;
    private final DeliveryExpressMapper carriers;
    private final TradeMemberAccess members;
    private final SecurityFrameworkService security;
    private final Validator validator;
    private final TransactionTemplate write;
    public TradeOrderFulfillmentService(TradeOrderFulfillmentMapper orders,DeliveryExpressMapper carriers,
            TradeMemberAccess members,SecurityFrameworkService security,Validator validator,PlatformTransactionManager manager) {
        this.orders=orders;this.carriers=carriers;this.members=members;this.security=security;this.validator=validator;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    public List<DeliveryExpressDO> carrierOptions() {
        merchant("trade:order:delivery","trade:order:update-logistics");
        List<DeliveryExpressDO> rows=carriers.enabled();
        if(rows.size()>200)throw exception0(503,"启用承运商目录超出范围");return rows;
    }
    public void deliver(TradeOrderDeliveryReqVO request) {
        validate(request);LoginUser actor=merchant("trade:order:delivery");long checkedAt=System.nanoTime();
        write.executeWithoutResult(status -> {
            freshMerchant(actor,checkedAt);
            TradeOrderDO order=required(actor.getTenantId(),request.getId());
            freshMerchant(actor,checkedAt);
            if(Arrays.asList(20,30).contains(order.getStatus()) && Boolean.TRUE.equals(order.getPayStatus()) && order.getDeliveryTime()!=null && sameParcel(order,request))return;
            version(order,request.getExpectedVersion());
            if(order.getStatus()!=10 || !Boolean.TRUE.equals(order.getPayStatus()))throw exception0(409,"仅已付款待发货订单可以发货");
            noPendingAfterSale(order);
            List<TradeOrderItemDO> lines=lines(order);int deliverable=0;
            for(TradeOrderItemDO line:lines) {
                if(line.getDeliveryCount()!=0)throw exception0(409,"原订单已有发货数量，请先核对历史");
                deliverable=Math.addExact(deliverable,line.getCount()-line.getRefundCount());
            }
            if(deliverable==0)throw exception0(409,"原商品已无可履约数量，请核对退款进度");
            DeliveryExpressDO carrier=carrier(request.getLogisticsId());LocalDateTime instant=now();
            if(orders.deliveryItems(actor.getTenantId(),order.getId())<=0)throw exception0(409,"订单商品已变化");
            // JDBC可能返回匹配行数或实际变化行数，重读数量事实而不猜测更新计数。
            for(TradeOrderItemDO line:lines(order))
                if(line.getDeliveryCount()!=line.getCount()-line.getRefundCount())throw new IllegalStateException("原商品发货数量保存不完整");
            changed(orders.deliver(actor.getTenantId(),order.getId(),order.getAuthVersion(),carrier.getId(),carrier.getCode(),carrier.getName(),request.getLogisticsNo(),instant));
            log(order,actor.getId(),"ADMIN","ADMIN_DELIVERY",20,parcelLog(order,carrier.getId(),carrier.getName(),request.getLogisticsNo(),null),instant);
            event(order,"ORDER_DELIVERED",instant);
            freshMerchant(actor,checkedAt);
        });
    }

    public void correctLogistics(TradeOrderLogisticsUpdateReqVO request) {
        validate(request);String reason=request.getReason().trim();
        if(reason.isEmpty() || reason.chars().anyMatch(Character::isISOControl))throw exception0(400,"请填写有效单号更正原因");
        LoginUser actor=merchant("trade:order:update-logistics");long checkedAt=System.nanoTime();
        write.executeWithoutResult(status -> {
            freshMerchant(actor,checkedAt);TradeOrderDO order=required(actor.getTenantId(),request.getId());
            freshMerchant(actor,checkedAt);
            if(!Arrays.asList(20,30).contains(order.getStatus()) || !Boolean.TRUE.equals(order.getPayStatus()) || order.getDeliveryTime()==null)
                throw exception0(409,"仅已发货订单可以更正物流信息");
            if(sameParcel(order,request))return;
            version(order,request.getExpectedVersion());
            Long carrierId=order.getLogisticsId();String carrierCode=order.getLogisticsCode(),carrierName=order.getLogisticsName();
            if(!Objects.equals(carrierId,request.getLogisticsId())) {
                DeliveryExpressDO carrier=carrier(request.getLogisticsId());
                carrierId=carrier.getId();carrierCode=carrier.getCode();carrierName=carrier.getName();
            }
            LocalDateTime instant=now();
            changed(orders.correct(actor.getTenantId(),order.getId(),order.getAuthVersion(),carrierId,carrierCode,carrierName,request.getLogisticsNo(),instant));
            log(order,actor.getId(),"ADMIN","ADMIN_LOGISTICS_UPDATE",order.getStatus(),parcelLog(order,carrierId,carrierName,request.getLogisticsNo(),reason),instant);
            // 从不修改delivery_time/receive_time/商品数量，避免续期自动收货或伪造二次发货。
            freshMerchant(actor,checkedAt);
        });
    }

    public void receiveByMember(HttpServletRequest request,String store,AppTradeOrderReceiveReqVO body) {
        validate(body);outside();members.check(request,store);LoginUser actor=members.actor();long checkedAt=System.nanoTime();
        write.executeWithoutResult(status -> {
            freshMember(actor,checkedAt);TradeOrderDO order=required(actor.getTenantId(),body.getId());
            freshMember(actor,checkedAt);
            if(!Objects.equals(order.getUserId(),actor.getId()))throw exception0(404,"本人订单不存在");
            if(order.getStatus()==30)return;
            version(order,body.getExpectedVersion());noPendingAfterSale(order);
            receive(order,actor.getId(),"MEMBER",false,now());freshMember(actor,checkedAt);
        });
    }

    /** 仅可信后台任务。false表示未达到条件/售后暂停，不代表收货成功。 */
    public boolean receiveBySystem(Long id) {
        outside();positive(id);Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || SecurityFrameworkUtils.getLoginUser()!=null || RequestContextHolder.getRequestAttributes()!=null)
            throw exception0(403,"自动收货仅允许可信租户任务");
        Boolean done=write.execute(status -> {
            TradeOrderDO order=required(tenant,id);
            if(order.getStatus()==30)return true;
            if(order.getAfterSalePendingCount()==null)throw exception0(503,"原订单售后进度不完整");
            if(order.getStatus()!=20 || order.getAfterSalePendingCount()!=0
                    || order.getDeliveryTime()==null || now().isBefore(order.getDeliveryTime().plusDays(14)))return false;
            receive(order,0L,"SYSTEM",true,now());return true;
        });
        if(done==null)throw new IllegalStateException("自动收货未返回结果");return done;
    }
    private void receive(TradeOrderDO order,Long actor,String actorType,boolean automatic,LocalDateTime instant) {
        if(order.getStatus()!=20 || !Boolean.TRUE.equals(order.getPayStatus()) || order.getDeliveryTime()==null || instant.isBefore(order.getDeliveryTime()))
            throw exception0(409,"原订单不在可确认收货阶段");
        noPendingAfterSale(order);int delivered=0;
        for(TradeOrderItemDO line:lines(order))delivered=Math.addExact(delivered,line.getDeliveryCount());
        if(delivered<=0)throw exception0(409,"原发货数量不完整，请核对物流进度");
        changed(orders.receive(order.getTenantId(),order.getId(),order.getAuthVersion(),automatic,instant));
        log(order,actor,actorType,automatic?"SYSTEM_RECEIVE":"MEMBER_RECEIVE",30,null,instant);
        event(order,"ORDER_RECEIVED",instant);
    }
    private List<TradeOrderItemDO> lines(TradeOrderDO order) {
        List<TradeOrderItemDO> lines=orders.items(order.getTenantId(),order.getId());
        if(lines.isEmpty() || lines.size()>200)throw exception0(503,"原订单商品快照不完整");
        int quantity=0;
        for(TradeOrderItemDO line:lines) {
            if(!Objects.equals(line.getOrderId(),order.getId()) || !Objects.equals(line.getTenantId(),order.getTenantId()) || !Objects.equals(line.getUserId(),order.getUserId())
                    || line.getCount()==null || line.getCount()<1 || line.getRefundCount()==null || line.getRefundCount()<0 || line.getRefundCount()>line.getCount()
                    || line.getDeliveryCount()==null || line.getDeliveryCount()<0 || line.getDeliveryCount()>line.getCount())throw exception0(503,"原订单商品履约数量不完整");
            quantity=Math.addExact(quantity,line.getCount());
        }
        if(!Integer.valueOf(quantity).equals(order.getProductCount()))throw exception0(503,"原订单商品数量不一致");return lines;
    }
    private DeliveryExpressDO carrier(Long id) {
        DeliveryExpressDO carrier=carriers.lockEnabled(id);
        if(carrier==null)throw exception0(409,"承运商不存在或已停用");return carrier;
    }
    private TradeOrderDO required(Long tenant,Long id) {
        TradeOrderDO order=orders.lock(tenant,id);if(order==null)throw exception0(404,"本租户订单不存在");return order;
    }
    private void noPendingAfterSale(TradeOrderDO order) {
        if(order.getAfterSalePendingCount()==null)throw exception0(503,"原订单售后进度不完整");
        if(order.getAfterSalePendingCount()!=0)throw exception0(409,"订单存在进行中售后，暂不能发货或收货");
    }
    private boolean sameParcel(TradeOrderDO order,TradeOrderDeliveryReqVO body) {
        return Objects.equals(order.getLogisticsId(),body.getLogisticsId()) && Objects.equals(order.getLogisticsNo(),body.getLogisticsNo());
    }
    private void version(TradeOrderDO order,Long expected) {
        if(!Objects.equals(order.getAuthVersion(),expected))throw exception0(409,"订单版本已变化，请重新读取");
    }
    private TradeOrderDeliveryLogDTO parcelLog(TradeOrderDO order,Long carrier,String name,String no,String reason) {
        TradeOrderDeliveryLogDTO details=new TradeOrderDeliveryLogDTO();
        details.setPreviousLogisticsId(order.getLogisticsId());details.setPreviousLogisticsName(order.getLogisticsName());details.setPreviousLogisticsNo(order.getLogisticsNo());
        details.setLogisticsId(carrier);details.setLogisticsName(name);details.setLogisticsNo(no);details.setReason(reason);return details;
    }
    private void log(TradeOrderDO order,Long actor,String actorType,String action,int to,TradeOrderDeliveryLogDTO details,LocalDateTime instant) {
        if(orders.log(order.getTenantId(),order.getId(),actor,actorType,action,order.getStatus(),to,Math.addExact(order.getAuthVersion(),1),
                details==null?null:JsonUtils.toJsonString(details),instant)!=1)throw new IllegalStateException("履约日志保存失败");
    }
    private void event(TradeOrderDO order,String type,LocalDateTime instant) {
        String trace=MDC.get("traceId");
        if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("orderId",order.getId());payload.put("traceId",trace);
        if(orders.event(UUID.randomUUID().toString(),order.getTenantId(),order.getId(),type,Math.addExact(order.getAuthVersion(),1),JsonUtils.toJsonString(payload),instant)!=1)
            throw new IllegalStateException("履约待发送事件保存失败");
    }
    private LoginUser merchant(String... permissions) {
        outside();LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0 || actor.getTenantId()>9_007_199_254_740_991L
                || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"履约操作仅限当前商家员工");
        if(!security.hasAnyPermissions(permissions))throw exception0(403,"没有订单履约操作权限");
        // 已有订单的履约义务不随店铺停止新购买而抹除，不调用普通经营开关。
        return actor;
    }
    private void freshMerchant(LoginUser actor,long checkedAt) {
        LoginUser current=SecurityFrameworkUtils.getLoginUser();
        if(current==null || !Objects.equals(current.getId(),actor.getId()) || !Objects.equals(current.getTenantId(),actor.getTenantId())
                || current.getExpiresTime()==null || !current.getExpiresTime().isAfter(LocalDateTime.now())
                || !Objects.equals(TenantContextHolder.getTenantId(),actor.getTenantId()) || TenantContextHolder.isIgnore())throw exception0(403,"当前员工身份已变化");
        recent(checkedAt);
    }
    private void freshMember(LoginUser actor,long checkedAt) {
        LoginUser current=members.actor();
        if(!Objects.equals(current.getId(),actor.getId()) || !Objects.equals(current.getTenantId(),actor.getTenantId()))throw exception0(403,"当前会员已变化");
        recent(checkedAt);
    }
    private void recent(long checkedAt){if(System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"资格核对已过时，请查询原订单结果");}
    private void validate(Object request){if(request==null || !validator.validate(request).isEmpty())throw exception0(400,"履约参数不正确");}
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("履约入口须在外层事务之外");}
    private void positive(Long id){if(id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(400,"订单编号不正确");}
    private void changed(int rows){if(rows!=1)throw exception0(409,"订单状态或版本已变化，请查询原结果");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
