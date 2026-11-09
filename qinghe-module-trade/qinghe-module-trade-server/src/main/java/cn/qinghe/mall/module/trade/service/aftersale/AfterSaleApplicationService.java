package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.*;
import cn.qinghe.mall.module.trade.dal.dataobject.order.*;
import cn.qinghe.mall.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderFulfillmentMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.service.aftersale.dto.*;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原createAfterSale：校验原项→保存申请→更新售后状态→日志。
 * 当前同原订单父锁串行化申请/发货/收货，持久原单位；不执行审核/资金/库存调用。
 * @author 芋道源码
 */
@Service
public class AfterSaleApplicationService {
    private final TradeMemberAccess access;
    private final TradeOrderFulfillmentMapper orders;
    private final AfterSaleMapper sales;
    private final AfterSaleRefundCalculator calculator;
    private final Validator validator;
    private final TransactionTemplate write;
    public AfterSaleApplicationService(TradeMemberAccess access,TradeOrderFulfillmentMapper orders,AfterSaleMapper sales,
            AfterSaleRefundCalculator calculator,Validator validator,PlatformTransactionManager manager) {
        this.access=access;this.orders=orders;this.sales=sales;this.calculator=calculator;this.validator=validator;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }
    public AfterSaleApplyResult apply(HttpServletRequest request,String store,AppAfterSaleCreateReqVO body) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("售后入口不能位于外层事务内");
        if(body==null || !validator.validate(body).isEmpty())throw exception0(400,"售后申请参数不正确");
        String reason=body.getApplyReason().trim(),description=body.getApplyDescription().trim();
        if(reason.isEmpty() || reason.chars().anyMatch(Character::isISOControl) || description.chars().anyMatch(c -> Character.isISOControl(c)&&c!='\n'&&c!='\r'))
            throw exception0(400,"请填写有效申请原因和描述");
        // 停止新购买不取消历史售后义务；不要求canPurchase=true，不在数据库锁内访问入口服务。
        access.check(request,store);LoginUser actor=access.actor();long checkedAt=System.nanoTime();
        try {
            return write.execute(status -> {
                fresh(actor,checkedAt);
                TradeOrderDO order=orders.lock(actor.getTenantId(),body.getOrderId());fresh(actor,checkedAt);
                if(order==null || !Objects.equals(order.getUserId(),actor.getId()))throw exception0(404,"本人订单不存在");
                AfterSaleDO old=sales.request(actor.getTenantId(),actor.getId(),body.getRequestId());
                if(old!=null){same(old,body,reason,description);return result(old);}
                int pending=sales.pending(actor.getTenantId(),order.getId());
                if(!Integer.valueOf(pending).equals(order.getAfterSalePendingCount()) || pending>=200)throw exception0(503,"原订单售后进度需先核对");
                List<TradeOrderItemDO> items=orders.items(actor.getTenantId(),order.getId());
                if(items.isEmpty() || items.size()>200)throw exception0(503,"原订单商品快照不完整");
                TradeOrderItemDO item=items.stream().filter(line -> Objects.equals(line.getId(),body.getOrderItemId())).findFirst().orElse(null);
                if(item==null)throw exception0(404,"本人原订单商品不存在");
                if(sales.activeItem(actor.getTenantId(),item.getId())!=0)throw exception0(409,"此商品已有进行中售后，请先查询原申请");
                List<AfterSaleUnitDO> held=sales.occupied(actor.getTenantId(),item.getId());Set<Integer> units=new HashSet<>();int refunded=0;
                for(AfterSaleUnitDO unit:held) {
                    if(!Objects.equals(unit.getOrderId(),order.getId()) || !Objects.equals(unit.getOrderItemId(),item.getId())
                            || !Objects.equals(unit.getTenantId(),actor.getTenantId()) || !units.add(unit.getUnitNo()))throw exception0(503,"原商品单位占用归属不完整");
                    if("REFUNDED".equals(unit.getState()))refunded++;
                    else if(!"RESERVED".equals(unit.getState()))throw exception0(503,"原商品占用状态不完整");
                }
                if(!Integer.valueOf(refunded).equals(item.getRefundCount()))throw exception0(503,"原商品已退数量与单位账不一致");
                LocalDateTime instant=now();
                AfterSaleUnitPlan plan=calculator.plan(order,item,body.getWay(),body.getCount(),body.getExpectedVersion(),units,instant);
                long reserved=Math.addExact(sales.reservedGoods(actor.getTenantId(),order.getId()),order.getDeliveryRefundReserved());
                if(Math.addExact(Math.addExact(order.getRefundPrice(),reserved),plan.getGoodsRefundPrice())>order.getPayPrice())
                    throw exception0(409,"申请超过原订单剩余可退金额");
                AfterSaleDO row=new AfterSaleDO();row.setTenantId(actor.getTenantId());row.setUserId(actor.getId());row.setRequestId(body.getRequestId());
                row.setNo("AS"+UUID.randomUUID().toString().replace("-",""));row.setOrderId(order.getId());row.setOrderItemId(item.getId());row.setOrderVersion(order.getAuthVersion());
                row.setStatus(10);row.setWay(plan.getWay());row.setCount(plan.getCount());row.setGoodsRefundPrice(plan.getGoodsRefundPrice());row.setOriginalDeliveryShare(plan.getOriginalDeliveryShare());
                row.setApplyReason(reason);row.setApplyDescription(description);row.setAuthVersion(0L);row.setCreateTime(instant);row.setUpdateTime(instant);
                if(sales.insert(row)!=1 || row.getId()==null || row.getId()<=0 || row.getId()>9_007_199_254_740_991L)throw new IllegalStateException("售后申请保存失败");
                if(sales.units(actor.getTenantId(),row.getId(),order.getId(),item.getId(),plan.getUnits())!=plan.getCount()
                        || sales.occupyOrder(actor.getTenantId(),actor.getId(),order.getId(),order.getAuthVersion(),instant)!=1
                        || sales.log(actor.getTenantId(),row.getId(),actor.getId(),instant)!=1)throw new IllegalStateException("售后数量占用或日志保存失败");
                long nextVersion=Math.addExact(order.getAuthVersion(),1);
                if(orders.log(actor.getTenantId(),order.getId(),actor.getId(),"MEMBER","AFTERSALE_APPLY",order.getStatus(),order.getStatus(),nextVersion,null,instant)!=1)
                    throw new IllegalStateException("原订单售后日志保存失败");
                String trace=MDC.get("traceId");if(trace==null || !trace.matches("[A-Za-z0-9_-]{1,64}"))trace=UUID.randomUUID().toString();
                Map<String,Object> payload=new LinkedHashMap<>();payload.put("orderId",order.getId());payload.put("afterSaleId",row.getId());payload.put("traceId",trace);
                if(orders.event(UUID.randomUUID().toString(),actor.getTenantId(),order.getId(),"AFTERSALE_APPLIED",nextVersion,JsonUtils.toJsonString(payload),instant)!=1)
                    throw new IllegalStateException("售后待发送事件保存失败");
                fresh(actor,checkedAt);return result(row);
            });
        } catch(DuplicateKeyException conflict) {
            // 不在已经失败的事务里继续写。并发同请求查回原回执；异参/其他唯一冲突拒绝。
            fresh(actor,checkedAt);AfterSaleDO old=sales.request(actor.getTenantId(),actor.getId(),body.getRequestId());
            if(old==null)throw exception0(409,"申请或原商品占用已变化，请查询原请求");
            same(old,body,reason,description);return result(old);
        }
    }
    private void same(AfterSaleDO row,AppAfterSaleCreateReqVO body,String reason,String description) {
        if(!Objects.equals(row.getOrderId(),body.getOrderId()) || !Objects.equals(row.getOrderItemId(),body.getOrderItemId()) || !Objects.equals(row.getOrderVersion(),body.getExpectedVersion())
                || !Objects.equals(row.getWay(),body.getWay()) || !Objects.equals(row.getCount(),body.getCount()) || !Objects.equals(row.getApplyReason(),reason) || !Objects.equals(row.getApplyDescription(),description))
            throw exception0(409,"同一申请号内容不同，请查询原申请");
    }
    private void fresh(LoginUser actor,long checkedAt) {
        LoginUser current=access.actor();
        if(!Objects.equals(actor.getId(),current.getId()) || !Objects.equals(actor.getTenantId(),current.getTenantId()) || System.nanoTime()-checkedAt>5_000_000_000L)
            throw exception0(503,"身份核对已过时，请查询原申请");
    }
    private AfterSaleApplyResult result(AfterSaleDO row){return new AfterSaleApplyResult(row.getId(),row.getNo(),row.getStatus(),row.getCount(),row.getGoodsRefundPrice());}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
}
