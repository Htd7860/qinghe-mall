package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.exception.ServiceException;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderPrepareMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderOutboxMapper;
import cn.qinghe.mall.module.trade.service.message.TradeOrderEventEnvelope;
import cn.qinghe.mall.module.trade.enums.order.TradeOrderStatusEnum;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.framework.order.OrderResourceClient;
import cn.qinghe.mall.module.trade.service.price.bo.TradeSettlementSnapshotBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原createOrder：构造订单/商品项、保存、清购物车与记录日志。
 * 资源RPC在本地事务外；主订单+原项id/分摊+CREATED共用准备行锁，在一个事务提交。
 * 由消费者HTTP编排调用；正金额订单另行准备支付，不自动发起渠道付款。MQ创建worker仍待。
 * @author 芋道源码
 */
@Service
@Slf4j
public class TradeOrderCreationService {
    private final TradeMemberAccess access;
    private final TradeOrderPrepareMapper preparations;
    private final TradeOrderMapper orders;
    private final TradeOrderOutboxMapper events;
    private final CartMapper carts;
    private final OrderResourceClient resources;
    private final TradeOrderCreationCompensationService compensation;
    private final TransactionTemplate write;

    public TradeOrderCreationService(TradeMemberAccess access,TradeOrderPrepareMapper preparations,TradeOrderMapper orders,
            CartMapper carts,OrderResourceClient resources,TradeOrderCreationCompensationService compensation,PlatformTransactionManager manager,
            TradeOrderOutboxMapper events) {
        this.access=access;this.preparations=preparations;this.orders=orders;this.carts=carts;
        this.resources=resources;this.compensation=compensation;
        this.events=events;
        write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
    }

    /** 原准备编号来自prepare；不接收外部会员、金额、收件快照或资源成功标志。 */
    public Long create(HttpServletRequest request,String store,Long preparationId) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("创建流程不能放在外层事务内");
        if(preparationId==null || preparationId<=0 || preparationId>9_007_199_254_740_991L)throw exception0(400,"订单准备编号不正确");
        MemberStorefrontRespDTO entry=access.check(request,store);
        LoginUser user=access.actor();
        TradeOrderPrepareDO original=preparations.get(user.getTenantId(),preparationId);
        owned(original,user);
        if("CREATED".equals(original.getStage()))return existing(user,preparationId);
        try {
            if(!Boolean.TRUE.equals(entry.getCanPurchase()))throw exception0(403,"店铺暂不支持新购买");
            String stage=write.execute(status -> {
                TradeOrderPrepareDO row=preparations.lockProgress(user.getTenantId(),preparationId);
                owned(row,user);
                if("CREATED".equals(row.getStage()))return row.getStage();
                active(row);
                if("PREPARED".equals(row.getStage()))transition(row,"PROCESSING");
                return "PROCESSING";
            });
            if("CREATED".equals(stage))return existing(user,preparationId);
            if(!"PROCESSING".equals(stage))throw new IllegalStateException("创建准备未进入处理中");

            resources.deductStock(preparationId);
            resources.reserveCoupon(preparationId);
            resources.useCoupon(preparationId);

            // 每次此RPC均经真实MEMBER资格链；锁内不跨服务。资格读取到提交前最多5秒。
            long checkedAt=System.nanoTime();
            MemberStorefrontRespDTO fresh=access.check(request,store);
            if(!Boolean.TRUE.equals(fresh.getCanPurchase()))throw exception0(403,"店铺暂不支持新购买");
            Long id=write.execute(status -> save(user,preparationId,fresh,checkedAt));
            if(id==null)throw new IllegalStateException("正式订单保存未返回结果");
            return id;
        } catch(ServiceException error) {
            // 明确业务拒绝才能发起不可逆补偿；503/超时/数据库提交未知保留原PROCESSING查询恢复。
            if(Arrays.asList(400,401,403,404,409).contains(error.getCode())) {
                try {compensation.compensate(preparationId);} catch(RuntimeException uncertain) {
                    log.warn("订单创建或补偿结果待确认 tenant={}, preparation={}",user.getTenantId(),preparationId);
                    throw exception0(503,"创建结果待确认，请查询原准备编号");
                }
            }
            throw error;
        } catch(RuntimeException uncertain) {
            log.warn("订单创建结果待确认 tenant={}, preparation={}",user.getTenantId(),preparationId,uncertain);
            throw exception0(503,"创建结果待确认，请查询原准备编号");
        }
    }

    private Long save(LoginUser user,Long id,MemberStorefrontRespDTO fresh,long checkedAt) {
        recent(user,checkedAt);
        // 与购物车修改/准备保存保持父行优先锁序；补偿只锁准备行且不等待购物车。
        Long cartVersion=carts.lockVersion(user.getTenantId(),user.getId());
        if(cartVersion==null)throw exception0(503,"原购物车主体不存在");
        TradeOrderPrepareDO progress=preparations.lockProgress(user.getTenantId(),id);
        owned(progress,user);
        if("CREATED".equals(progress.getStage()))return existing(user,id);
        active(progress);
        if(!"PROCESSING".equals(progress.getStage()))throw exception0(409,"原准备不在创建阶段");
        TradeOrderPrepareDO row=preparations.get(user.getTenantId(),id);
        TradeSettlementSnapshotBO snapshot=JsonUtils.parseObject(row.getSnapshot(),TradeSettlementSnapshotBO.class);
        if(snapshot==null || snapshot.getAddress()==null || !fresh.equals(snapshot.getStorefront())
                || !Objects.equals(user.getTenantId(),snapshot.getAddress().getTenantId())
                || !Objects.equals(user.getId(),snapshot.getAddress().getUserId()))throw exception0(409,"原订单入口或收件快照不一致，请重新确认");
        List<TradeOrderPrepareItemDO> items=preparations.items(user.getTenantId(),id);
        totals(row,items);
        if(orders.owned(user.getTenantId(),user.getId(),id)!=null)throw exception0(503,"订单与准备阶段不一致，需核查原结果");
        TradeOrderDO order=build(row,snapshot);
        if(orders.insert(order)!=1 || orders.insertItems(user.getTenantId(),user.getId(),id)!=items.size())
            throw new IllegalStateException("正式订单或商品快照保存失败");

        int removed=0;
        for(TradeOrderPrepareItemDO item:items) {
            int count=carts.retirePurchased(user.getTenantId(),user.getId(),item.getCartId(),item.getSkuId(),item.getCount(),item.getCartLineVersion());
            if(count<0 || count>1)throw new IllegalStateException("购物车清理结果异常");
            removed+=count;
        }
        if(removed>0 && carts.bump(user.getTenantId(),user.getId(),cartVersion)!=1)throw exception0(409,"购物车版本已变化");
        if(orders.logCreated(user.getTenantId(),user.getId(),id,order.getCreateTime())!=1)throw new IllegalStateException("订单创建日志保存失败");
        String expiryPayload=JsonUtils.toJsonString(Collections.singletonMap("dueAt",TradeOrderEventEnvelope.epochCeiling(order.getPayDeadline())));
        if(events.insertExpiry(user.getTenantId(),id,UUID.randomUUID().toString(),expiryPayload,order.getPayDeadline(),order.getCreateTime())!=1)
            throw new IllegalStateException("原订单到期事件保存失败");
        transition(progress,"CREATED");
        recent(user,checkedAt);
        if(!now().isBefore(row.getPrepareDeadline()))throw exception0(409,"订单准备已到期，请重新确认");
        return id;
    }

    private TradeOrderDO build(TradeOrderPrepareDO row,TradeSettlementSnapshotBO snapshot) {
        TradeOrderDO order=new TradeOrderDO();
        order.setId(row.getId());order.setTenantId(row.getTenantId());order.setUserId(row.getUserId());
        order.setNo(row.getNo());order.setType(0);order.setStatus(TradeOrderStatusEnum.UNPAID.getStatus());order.setAuthVersion(0L);
        order.setUserRemark(row.getRemark());order.setProductCount(row.getProductCount());order.setTotalPrice(row.getTotalPrice());
        order.setDiscountPrice(row.getDiscountPrice());order.setDeliveryPrice(row.getDeliveryPrice());order.setPayPrice(row.getPayPrice());
        order.setCouponId(snapshot.getCoupon()==null?null:snapshot.getCoupon().getId());order.setPayStatus(false);order.setRefundPrice(0L);
        order.setReceiverName(snapshot.getAddress().getName());order.setReceiverMobile(snapshot.getAddress().getMobile());
        order.setReceiverAreaId(snapshot.getAddress().getAreaId());order.setReceiverDetailAddress(snapshot.getAddress().getDetailAddress());
        order.setCreateTime(now());order.setUpdateTime(order.getCreateTime());order.setPayDeadline(order.getCreateTime().plusMinutes(30));
        return order;
    }

    private void totals(TradeOrderPrepareDO row,List<TradeOrderPrepareItemDO> items) {
        if(items==null || items.isEmpty() || items.size()>200)throw exception0(503,"原订单商品快照不完整");
        long goods=0,discount=0,delivery=0,pay=0,count=0;
        for(TradeOrderPrepareItemDO item:items) {
            goods=Math.addExact(goods,item.getGoodsPrice());discount=Math.addExact(discount,item.getDiscountPrice());
            delivery=Math.addExact(delivery,item.getDeliveryPrice());pay=Math.addExact(pay,item.getPayPrice());count=Math.addExact(count,item.getCount());
        }
        if(!Objects.equals(goods,row.getTotalPrice()) || !Objects.equals(discount,row.getDiscountPrice())
                || !Objects.equals(delivery,row.getDeliveryPrice()) || !Objects.equals(pay,row.getPayPrice())
                || row.getProductCount()==null || count!=row.getProductCount())throw exception0(503,"原订单商品金额与汇总不一致");
    }
    private void active(TradeOrderPrepareDO row) {
        if(!Arrays.asList("PREPARED","PROCESSING").contains(row.getStage()) || row.getPrepareDeadline()==null
                || !now().isBefore(row.getPrepareDeadline()))throw exception0(409,"原订单准备已关闭或到期");
    }
    private void owned(TradeOrderPrepareDO row,LoginUser user) {
        if(row==null || !Objects.equals(user.getTenantId(),row.getTenantId()) || !Objects.equals(user.getId(),row.getUserId()))
            throw exception0(404,"本人订单准备记录不存在");
    }
    private Long existing(LoginUser user,Long id) {
        if(orders.owned(user.getTenantId(),user.getId(),id)==null)throw exception0(503,"原订单结果不完整，请查询原请求");
        return id;
    }
    private void transition(TradeOrderPrepareDO row,String to) {
        if(preparations.createStage(row.getTenantId(),row.getId(),row.getStage(),to,row.getAuthVersion())!=1)
            throw exception0(409,"订单准备阶段或版本已变化");
    }
    private void recent(LoginUser user,long checkedAt) {
        LoginUser current=access.actor();
        if(!Objects.equals(user.getTenantId(),current.getTenantId()) || !Objects.equals(user.getId(),current.getId()))throw exception0(403,"当前会员身份已变化");
        if(System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"资格核对已过时，请按原请求重试");
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
