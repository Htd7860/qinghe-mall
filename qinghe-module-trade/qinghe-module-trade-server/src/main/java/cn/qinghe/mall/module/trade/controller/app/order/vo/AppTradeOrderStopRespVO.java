package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;

/** stopped=true仅说明未受理原请求已被报价锁屏障永久阻止；不是正式订单取消回执。 */
@Data
public class AppTradeOrderStopRespVO {
    private Boolean stopped;
    private AppTradeOrderCreateRespVO result;
}
