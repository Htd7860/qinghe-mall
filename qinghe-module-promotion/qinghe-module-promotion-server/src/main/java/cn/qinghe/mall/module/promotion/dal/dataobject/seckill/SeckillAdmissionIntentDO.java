package cn.qinghe.mall.module.promotion.dal.dataobject.seckill;
import lombok.Data;
import java.time.LocalDateTime;

/** 预占前的持久栅栏：0准备/10已受理/20永久拒绝；不等于库存已经占用。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillAdmissionIntentDO {
    private Long tenantId;
    private String requestNo;
    private Long memberId;
    private String clientRequestId;
    private String requestDigest;
    private Long activityId;
    private Integer generation;
    private Long skuId;
    private String requestSnapshot;
    private String contextDigest;
    private Integer status;
    private Integer failureCode;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime quoteExpiresAt;
    private String cacheSyncState;
    private LocalDateTime cacheSyncedAt;
    private Integer recoveryAttempts;
    private LocalDateTime nextRecoveryAt;
    private Integer recoveryErrorCode;
}
