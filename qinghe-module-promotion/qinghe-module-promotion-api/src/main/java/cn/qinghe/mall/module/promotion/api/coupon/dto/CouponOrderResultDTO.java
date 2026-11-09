package cn.qinghe.mall.module.promotion.api.coupon.dto;
import lombok.Data;
/** 历史操作结果，不是券当前可用性。阶段1占用/2已核销/3占用已释放/4已退原券。 */
@Data
public class CouponOrderResultDTO {
    private Long orderId;
    private Long couponId;
    private Integer state;
    private String returnReason;
    private CouponQuoteDTO quote;
}
