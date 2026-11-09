package cn.qinghe.mall.module.promotion.service.seckill.dto;
import lombok.Data;
import java.time.OffsetDateTime;

/** 本店会员本人申请投影；无内部预占token/报价摘要/地址或其他会员字段。 */
@Data
public class SeckillRequestReceiptDTO {
    private Boolean found;
    private String requestNo;
    private String clientRequestId;
    private Long activityId;
    private Integer generation;
    private Long skuId;
    private Integer quantity;
    private Long goodsAmount;
    private Long shippingAmount;
    private Long payAmount;
    private Integer status;
    private Long orderId;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime reconcileDeadline;
    private OffsetDateTime payDeadline;
    private Long version;
    private Long serverTime;
}
