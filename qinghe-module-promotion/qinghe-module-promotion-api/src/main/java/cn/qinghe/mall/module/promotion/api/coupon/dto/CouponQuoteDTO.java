package cn.qinghe.mall.module.promotion.api.coupon.dto;
import lombok.Data;
import java.util.List;
/** 原couponPrice/行分摊职责；按B05持久保存稳定行/单位分摊，不依赖退款时的新价格。
 * @author 芋道源码
 */
@Data
public class CouponQuoteDTO {
    private Long couponId;
    private Long couponVersion;
    private Long eligiblePrice;
    private Long discountPrice;
    private List<Line> lines;
    @Data
    public static class Line {
        private Long lineId;
        private Long spuId;
        private Long skuId;
        private Long categoryId;
        private Integer quantity;
        private Long unitPrice;
        private Long goodsPrice;
        private Long discountPrice;
        private Long paidPrice;
        /** 第0..remainder-1个稳定单位额外优惠1分，其余单位取base。 */
        private Long unitDiscountBase;
        private Integer unitDiscountRemainder;
    }
}
