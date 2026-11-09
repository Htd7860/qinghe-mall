package cn.qinghe.mall.module.promotion.api.seckill.dto;
import lombok.Data;
import java.time.OffsetDateTime;
/** 本库实际终局/流水与缓存ACK白名单；不含地址、Token或任意写入参数。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillResourceReceiptDTO {
    private Long tenantId;
    private String requestNo;
    private Long memberId;
    private Long orderId;
    private String requestDigest;
    private String state;
    private String cacheState;
    private Long orderVersion;
    private Long payOrderId;
    private OffsetDateTime resourceTime;
    private Long requestVersion;
}
