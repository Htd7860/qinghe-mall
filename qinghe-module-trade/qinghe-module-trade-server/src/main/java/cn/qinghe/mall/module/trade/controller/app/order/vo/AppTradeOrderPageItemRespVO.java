package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;
import lombok.ToString;
import java.util.List;

/** 择取原订单分页/金额字段，时间统一epoch毫秒；预览最多3项，详情才含全部规格/地址。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AppTradeOrderPageItemRespVO {
    private Long id;
    private String no;
    private Integer status;
    private Boolean payStatus;
    private Long payOrderId;
    private Integer productCount;
    private Integer itemCount;
    private Long totalPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    private Long refundPrice;
    private Integer cancelType;
    private Boolean cancelResourcesReleased;
    private Long authVersion;
    private Long serverNow;
    private Long createdAt;
    private Long payDeadline;
    private Long paidAt;
    private Long cancelledAt;
    private Long logisticsId;
    private String logisticsName;
    private String logisticsNo;
    private Long deliveredAt;
    private Long receivedAt;
    /** 原首次发货+14天；进行中售后只暂停执行，不重置原时间。 */
    private Long autoReceiveAt;
    private Integer afterSalePendingCount;
    /** 仅原账是否尚有应付，不承诺支付渠道已经配置/可用。 */
    private Boolean requiresPayment;
    private List<AppTradeOrderItemRespVO> items;
}
