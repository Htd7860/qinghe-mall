package cn.qinghe.mall.module.promotion.dal.dataobject.coupon;
import lombok.Data;
import java.time.LocalDateTime;
/** 订单与原券的永久绑定/成功阶段记录，不软删除，不因退券允许原订单换券重新占用。 */
@Data
public class CouponOrderDO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long orderId;
    private Long couponId;
    private String requestDigest;
    private Integer state;
    private String quoteJson;
    private Long couponVersion;
    private Long authVersion;
    private String returnReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
