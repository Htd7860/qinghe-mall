package cn.qinghe.mall.module.promotion.dal.dataobject.coupon;
import lombok.Data;
/** 成功状态转换审计，与券和订单绑定的本地更新同事务；订单事件不冒充员工操作。 */
@Data
public class CouponAuditDO {
    private Long tenantId;
    private Long couponId;
    private Long userId;
    private Long orderId;
    private String action;
    private Integer fromStatus;
    private Integer toStatus;
    private Long fromVersion;
    private Long toVersion;
    private Long actorId;
    private String reason;
}
