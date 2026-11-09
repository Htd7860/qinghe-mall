package cn.qinghe.mall.module.promotion.controller.app.coupon.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** 原canTake投影及券面条件；剩余量只作展示，最终以领取事务为准。 @author 芋道源码 */
@Data @EqualsAndHashCode(callSuper=true)
public class AppCouponTemplateRespVO extends AppCouponRulesRespVO {
    private String description;
    private Integer validityType;
    private Integer fixedEndTerm;
    private Integer remainingCount;
    private Integer takeLimitCount;
    private Long takenCount;
    private Boolean canTake;
    private String unavailableReason;
}
