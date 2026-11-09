package cn.qinghe.mall.module.trade.dal.mysql.order;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeOperatingSummaryReqVO;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOperatingAggregateDTO;
import org.apache.ibatis.annotations.*;

/** 择取原TradeOrderStatisticsMapper COUNT/SUM职责；本地表/参数化tenant/真实事件时间。
 * @author owen
 */
@Mapper
public interface TradeOperatingStatisticsMapper {
    String STATUS="<if test='q.status!=null'> AND o.status=#{q.status}</if>";
    @Select("<script>SELECT COUNT(*) FROM trade_order o WHERE o.tenant_id=#{tenant} AND o.create_time&gt;=#{q.beginTime} AND o.create_time&lt;#{q.endTime}"+STATUS+"</script>")
    long created(@Param("tenant") Long tenant,@Param("q") TradeOperatingSummaryReqVO q);
    @Select("<script>SELECT COUNT(*) AS count,COALESCE(SUM(o.pay_price),0) AS amount,COALESCE(SUM(o.refund_price),0) AS refund_amount "
        +"FROM trade_order o WHERE o.tenant_id=#{tenant} AND o.pay_status=b'1' AND o.pay_time&gt;=#{q.beginTime} AND o.pay_time&lt;#{q.endTime}"+STATUS+"</script>")
    TradeOperatingAggregateDTO paid(@Param("tenant") Long tenant,@Param("q") TradeOperatingSummaryReqVO q);
    @Select("<script>SELECT COUNT(*) AS count,COALESCE(SUM(f.total_price),0) AS amount,COALESCE(SUM(f.goods_price),0) AS goods_amount,COALESCE(SUM(f.delivery_price),0) AS delivery_amount "
        +"FROM trade_after_sale_refund f JOIN trade_order o ON o.tenant_id=f.tenant_id AND o.id=f.order_id "
        +"WHERE f.tenant_id=#{tenant} AND o.tenant_id=#{tenant} AND f.status='SUCCESS' AND f.refund_time&gt;=#{q.beginTime} AND f.refund_time&lt;#{q.endTime}"+STATUS+"</script>")
    TradeOperatingAggregateDTO refunded(@Param("tenant") Long tenant,@Param("q") TradeOperatingSummaryReqVO q);
}
