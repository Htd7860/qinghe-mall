package cn.qinghe.mall.module.trade.controller.admin.order.vo;

import lombok.Data;
import java.util.List;

/** 择取原订单/原项组合；分页最多三项预览，不含会员身份或收件信息。
 * @author 芋道源码
 */
@Data
public class TradeOrderPageItemRespVO {
    private Long id;
    private String no;
    private Integer status;
    private Long authVersion;
    private Boolean payStatus;
    private Integer productCount;
    private Integer itemCount;
    private Long totalPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    private Long refundPrice;
    private Boolean cancelResourcesReleased;
    private Integer afterSalePendingCount;
    private Long logisticsId;
    private String logisticsName;
    private String logisticsNo;
    private Long createdAt;
    private Long paidAt;
    private Long deliveredAt;
    private Long receivedAt;
    private Long cancelledAt;
    private Long serverNow;
    private List<TradeOrderItemRespVO> items;
}
