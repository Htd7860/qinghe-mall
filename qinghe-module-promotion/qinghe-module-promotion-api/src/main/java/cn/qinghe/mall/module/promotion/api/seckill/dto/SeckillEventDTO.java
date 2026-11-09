package cn.qinghe.mall.module.promotion.api.seckill.dto;
import lombok.Data;

/** 固定消息身份；金额/地址/业务命令不放入Broker信封。 */
@Data
public class SeckillEventDTO {
    private String eventId;
    private Long tenantId;
    private String requestNo;
    private String eventType;
    private Long aggregateVersion;
    private Integer schemaVersion;
    private Long occurredAt;
}
