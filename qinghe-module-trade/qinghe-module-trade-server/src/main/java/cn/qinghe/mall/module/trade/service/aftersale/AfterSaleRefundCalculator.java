package cn.qinghe.mall.module.trade.service.aftersale;

import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.qinghe.mall.module.trade.dal.dataobject.aftersale.AfterSaleUnitDO;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.qinghe.mall.module.trade.enums.aftersale.AfterSaleWayEnum;
import cn.qinghe.mall.module.trade.service.aftersale.dto.AfterSaleUnitPlan;
import cn.qinghe.mall.module.trade.service.aftersale.dto.AfterSaleRefundBalanceDTO;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原AfterSaleServiceImpl.validateOrderItemApplicable的原订单资格/原实付上限职责。
 * 项目增量：数量型退款，固定原单位/优惠余分，严格7天窗口；不重读当前商品价或重新套券门槛。
 * 纯计算不查库/加锁/占用/打款。调用方必须在同一原订单父锁事务读取原项和已退/占用单位后保存结果。
 * @author 芋道源码
 */
@Component
public class AfterSaleRefundCalculator {
    private static final long MAX_SAFE=9_007_199_254_740_991L;

    public AfterSaleUnitPlan plan(TradeOrderDO order,TradeOrderItemDO item,Integer way,Integer count,
            Long expectedVersion,Set<Integer> occupiedUnitNos,LocalDateTime now) {
        validateSnapshot(order,item);
        if(now==null || expectedVersion==null || !Objects.equals(order.getAuthVersion(),expectedVersion))
            throw exception0(409,"原订单版本已变化，请重新读取");
        if(now.isBefore(order.getCreateTime()))throw exception0(503,"订单时间不完整");
        if(!Boolean.TRUE.equals(order.getPayStatus()) || !Arrays.asList(10,20,30).contains(order.getStatus()))
            throw exception0(409,"仅已付款且未关闭的订单可以申请售后");
        if(!Objects.equals(way,AfterSaleWayEnum.REFUND.getWay()) && !Objects.equals(way,AfterSaleWayEnum.RETURN_AND_REFUND.getWay()))
            throw exception0(400,"售后方式不正确");
        boolean shipped=order.getDeliveryTime()!=null;
        if((order.getStatus()==10 && shipped) || (Arrays.asList(20,30).contains(order.getStatus()) && !shipped)
                || shipped && (order.getDeliveryTime().isBefore(order.getCreateTime()) || now.isBefore(order.getDeliveryTime())))
            throw exception0(503,"原订单发货事实不完整");
        if(Objects.equals(way,AfterSaleWayEnum.RETURN_AND_REFUND.getWay()) && !shipped)
            throw exception0(409,"未发货订单仅支持退款");
        if(order.getStatus()==30) {
            if(order.getReceiveTime()==null || order.getReceiveTime().isBefore(order.getDeliveryTime()) || now.isBefore(order.getReceiveTime()))
                throw exception0(503,"原订单收货事实不完整");
            if(!now.isBefore(order.getReceiveTime().plusDays(7)))throw exception0(409,"收货后七天申请期限已过");
        } else if(order.getReceiveTime()!=null)throw exception0(503,"原订单收货状态不一致");
        if(count==null || count<1 || count>item.getCount())throw exception0(400,"售后数量不正确");
        if(occupiedUnitNos==null || occupiedUnitNos.size()>item.getCount())throw exception0(503,"原商品已退及占用记录不完整");
        Set<Integer> occupied=new HashSet<>(occupiedUnitNos);
        for(Integer unit:occupied)if(unit==null || unit<0 || unit>=item.getCount())throw exception0(503,"原商品占用单位超出范围");
        if(count>item.getCount()-occupied.size())throw exception0(409,"商品剩余可申请数量不足");

        List<AfterSaleUnitPlan.Unit> selected=new ArrayList<>();long goods=0,shipping=0;
        for(int unit=0;unit<item.getCount() && selected.size()<count;unit++) {
            if(occupied.contains(unit))continue;
            // 下单时已保存每件优惠的商及余数：前remainder件各多分摊1分，不重新均摊剩余商品。
            long discount=Math.addExact(item.getUnitDiscountBase(),unit<item.getUnitDiscountRemainder()?1L:0L);
            long unitGoods=Math.subtractExact(item.getPrice().longValue(),discount);
            long unitDelivery=Math.addExact(item.getUnitDeliveryBase(),unit<item.getUnitDeliveryRemainder()?1L:0L);
            goods=Math.addExact(goods,unitGoods);shipping=Math.addExact(shipping,unitDelivery);
            selected.add(new AfterSaleUnitPlan.Unit(unit,unitGoods,unitDelivery));
        }
        if(selected.size()!=count || goods<0 || goods>item.getGoodsPrice()-item.getDiscountPrice())throw exception0(503,"原商品退款分摊不完整");
        return new AfterSaleUnitPlan(order.getId(),item.getId(),expectedVersion,way,count,goods,shipping,
                Collections.unmodifiableList(selected));
    }

    public static void verifyReservedUnits(AfterSaleDO row,List<AfterSaleUnitDO> units) {
        if(units.size()!=row.getCount() || units.isEmpty() || units.size()>9999)throw exception0(503,"原售后占用数量不完整");
        long goods=0,delivery=0;Set<Integer> indexes=new HashSet<>();
        for(AfterSaleUnitDO unit:units) {
            if(!Objects.equals(unit.getTenantId(),row.getTenantId()) || !Objects.equals(unit.getAfterSaleId(),row.getId())
                    || !Objects.equals(unit.getOrderId(),row.getOrderId()) || !Objects.equals(unit.getOrderItemId(),row.getOrderItemId())
                    || !"RESERVED".equals(unit.getState()) || !indexes.add(unit.getUnitNo()))throw exception0(503,"原售后单位状态需先核对");
            goods=Math.addExact(goods,unit.getGoodsPrice());delivery=Math.addExact(delivery,unit.getOriginalDeliveryShare());
        }
        if(goods!=row.getGoodsRefundPrice() || delivery!=row.getOriginalDeliveryShare())throw exception0(503,"原售后占用金额需先核对");
    }
    /** 仅核对原成交快照，不重新施加新申请窗口，已受理售后可在窗口后继续处理。 */
    public static void validateSnapshot(TradeOrderDO order,TradeOrderItemDO item) {
        if(order==null || item==null || !positive(order.getId()) || !positive(order.getTenantId()) || !positive(order.getUserId()) || !positive(item.getId())
                || !Objects.equals(order.getId(),item.getOrderId()) || !Objects.equals(order.getTenantId(),item.getTenantId()) || !Objects.equals(order.getUserId(),item.getUserId()))
            throw exception0(503,"原订单和商品归属不完整");
        if(order.getAuthVersion()==null || order.getAuthVersion()<0 || order.getAuthVersion()>MAX_SAFE || order.getCreateTime()==null || order.getStatus()==null
                || item.getCount()==null || item.getCount()<1 || item.getCount()>9999 || item.getPrice()==null || item.getPrice()<1 || item.getPrice()>2_000_000_000
                || !amount(item.getGoodsPrice()) || !amount(item.getDiscountPrice()) || !amount(item.getDeliveryPrice()) || !amount(item.getPayPrice())
                || !amount(item.getUnitDiscountBase()) || !amount(item.getUnitDeliveryBase()) || item.getUnitDiscountRemainder()==null || item.getUnitDeliveryRemainder()==null
                || item.getUnitDiscountRemainder()<0 || item.getUnitDiscountRemainder()>=item.getCount() || item.getUnitDeliveryRemainder()<0 || item.getUnitDeliveryRemainder()>=item.getCount())
            throw exception0(503,"原商品单位金额快照不完整");
        if(item.getUnitDiscountBase()>item.getPrice() || item.getUnitDeliveryBase()>item.getDeliveryPrice()/item.getCount())
            throw exception0(503,"原商品单位分摊超出原金额");
        long quantity=item.getCount();long discounts=Math.addExact(Math.multiplyExact(item.getUnitDiscountBase(),quantity),item.getUnitDiscountRemainder());
        long delivery=Math.addExact(Math.multiplyExact(item.getUnitDeliveryBase(),quantity),item.getUnitDeliveryRemainder());
        if(item.getGoodsPrice()!=Math.multiplyExact(item.getPrice().longValue(),quantity) || discounts!=item.getDiscountPrice() || delivery!=item.getDeliveryPrice()
                || discounts>item.getGoodsPrice() || item.getUnitDiscountBase()+(item.getUnitDiscountRemainder()>0?1:0)>item.getPrice()
                || item.getPayPrice()!=Math.addExact(Math.subtractExact(item.getGoodsPrice(),discounts),delivery))
            throw exception0(503,"原商品单位分摊与成交金额不一致");
        if(!amount(order.getPayPrice()) || !amount(order.getRefundPrice()) || order.getRefundPrice()>order.getPayPrice())
            throw exception0(503,"原订单退款账不完整");
    }
    public static AfterSaleRefundBalanceDTO.Totals verifyBalances(TradeOrderDO order,List<TradeOrderItemDO> items,
            List<AfterSaleRefundBalanceDTO.Units> groups,AfterSaleRefundBalanceDTO balance) {
        if(items.isEmpty() || items.size()>200)throw exception0(503,"原订单商品快照不完整");
        if(order.getDeliveryRefundPrice()==null || order.getDeliveryRefundReserved()==null || order.getDeliveryPrice()==null
                || order.getDeliveryRefundPrice()<0 || order.getDeliveryRefundReserved()<0
                || Math.addExact(order.getDeliveryRefundPrice(),order.getDeliveryRefundReserved())>order.getDeliveryPrice())
            throw exception0(503,"原订单运费退款账不完整");
        Map<Long,TradeOrderItemDO> index=new HashMap<>();long quantity=0,goods=0,discount=0,freight=0,pay=0;
        for(TradeOrderItemDO item:items) {
            AfterSaleRefundCalculator.validateSnapshot(order,item);
            if(item.getRefundCount()==null || item.getRefundCount()<0 || item.getRefundCount()>item.getCount() || index.put(item.getId(),item)!=null)
                throw exception0(503,"原商品退款数量不完整");
            quantity=Math.addExact(quantity,item.getCount());goods=Math.addExact(goods,item.getGoodsPrice());discount=Math.addExact(discount,item.getDiscountPrice());
            freight=Math.addExact(freight,item.getDeliveryPrice());pay=Math.addExact(pay,item.getPayPrice());
        }
        if(quantity!=order.getProductCount() || goods!=order.getTotalPrice() || discount!=order.getDiscountPrice() || freight!=order.getDeliveryPrice() || pay!=order.getPayPrice())
            throw exception0(503,"原订单成交总额与商品快照不一致");
        Map<Long,UnitTotals> counted=new HashMap<>();
        if(groups.size()>400)throw exception0(503,"原单位汇总超出范围");
        for(AfterSaleRefundBalanceDTO.Units group:groups) {
            if(!index.containsKey(group.getOrderItemId()) || group.getQuantity()==null || group.getQuantity()<1 || group.getQuantity()>9999
                    || group.getGoodsPrice()==null || group.getGoodsPrice()<0)throw exception0(503,"原单位账归属或金额不完整");
            UnitTotals value=counted.computeIfAbsent(group.getOrderItemId(),key -> new UnitTotals());
            if("REFUNDED".equals(group.getState())){value.refundedQuantity=group.getQuantity();value.refundedGoods=group.getGoodsPrice();}
            else if("RESERVED".equals(group.getState())){value.reservedQuantity=group.getQuantity();value.reservedGoods=group.getGoodsPrice();}
            else throw exception0(503,"原单位账状态不正确");
        }
        UnitTotals sum=new UnitTotals();
        for(TradeOrderItemDO item:items) {
            UnitTotals value=counted.getOrDefault(item.getId(),new UnitTotals());
            if(value.refundedQuantity!=item.getRefundCount() || Math.addExact(value.refundedQuantity,value.reservedQuantity)>item.getCount()
                    || Math.addExact(value.refundedGoods,value.reservedGoods)>Math.subtractExact(item.getGoodsPrice(),item.getDiscountPrice()))
                throw exception0(503,"原商品单位数量或余额不一致");
            sum.refundedQuantity=Math.addExact(sum.refundedQuantity,value.refundedQuantity);sum.refundedGoods=Math.addExact(sum.refundedGoods,value.refundedGoods);
            sum.reservedGoods=Math.addExact(sum.reservedGoods,value.reservedGoods);
        }
        if(balance==null || !Objects.equals(balance.getSuccessDelivery(),order.getDeliveryRefundPrice()) || !Objects.equals(balance.getPendingDelivery(),order.getDeliveryRefundReserved())
                || balance.getSuccessGoods()!=sum.refundedGoods || Math.addExact(balance.getSuccessGoods(),balance.getSuccessDelivery())!=order.getRefundPrice()
                || Math.addExact(Math.addExact(order.getRefundPrice(),sum.reservedGoods),order.getDeliveryRefundReserved())>order.getPayPrice())
            throw exception0(503,"原运费、退款账或单位余额需先核对");
        return new AfterSaleRefundBalanceDTO.Totals(sum.refundedQuantity,sum.refundedGoods,sum.reservedGoods);
    }
    public static void verifyOriginalUnits(TradeOrderItemDO item,List<AfterSaleUnitDO> units) {
        for(AfterSaleUnitDO unit:units) {
            if(unit.getUnitNo()<0 || unit.getUnitNo()>=item.getCount())throw exception0(503,"原退款单位超出成交数量");
            long goods=Math.subtractExact(item.getPrice().longValue(),Math.addExact(item.getUnitDiscountBase(),unit.getUnitNo()<item.getUnitDiscountRemainder()?1L:0L));
            long delivery=Math.addExact(item.getUnitDeliveryBase(),unit.getUnitNo()<item.getUnitDeliveryRemainder()?1L:0L);
            if(unit.getGoodsPrice()!=goods || unit.getOriginalDeliveryShare()!=delivery)throw exception0(503,"原退款单位分摊需核对");
        }
    }
    private static class UnitTotals {long refundedQuantity;long refundedGoods;long reservedQuantity;long reservedGoods;}
    private static boolean amount(Long value){return value!=null && value>=0 && value<=MAX_SAFE;}
    private static boolean positive(Long value){return value!=null && value>0 && value<=MAX_SAFE;}
}
