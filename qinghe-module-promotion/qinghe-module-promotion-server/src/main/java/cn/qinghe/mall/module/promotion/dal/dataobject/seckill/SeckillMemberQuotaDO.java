package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;
import lombok.Data;
import java.time.LocalDateTime;

/** 每个发布代次的整场个人限额；已支付退款后不减少paidQuantity。 */
@Data
public class SeckillMemberQuotaDO {
    private Long tenantId;
    private Long activityId;
    private Integer generation;
    private Long memberId;
    private Integer heldQuantity;
    private Integer paidQuantity;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
