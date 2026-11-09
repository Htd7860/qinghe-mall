package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;
import lombok.ToString;

/** PREPARED/PROCESSING不叫创建成功；只有CREATED且order非空才存在正式订单，付款状态独立。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AppTradeOrderCreateRespVO {
    private Long preparationId;
    private String requestId;
    private String quoteId;
    private String stage;
    private Long serverNow;
    private AppTradeOrderPageItemRespVO order;
}
