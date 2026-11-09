package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;

/** 金额均为整数分的十进制字符串，避免汇总超过JavaScript安全整数；无订单/会员隐私。 */
@Data
public class TradeOperatingSummaryRespVO {
    private String timezone;
    private Long beginTime;
    private Long endTime;
    private Long queriedAt;
    private Integer status;
    private String createdOrderCount;
    private String paidOrderCount;
    private String paidAmount;
    private String successfulRefundCount;
    private String refundGoodsAmount;
    private String refundDeliveryAmount;
    private String refundAmount;
    private String periodNetAmount;
    private String paidCohortAccumulatedRefundAmount;
    private String paidCohortCurrentNetAmount;
}
