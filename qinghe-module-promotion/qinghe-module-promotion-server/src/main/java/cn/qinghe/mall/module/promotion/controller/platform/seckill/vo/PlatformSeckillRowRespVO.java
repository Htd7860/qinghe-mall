package cn.qinghe.mall.module.promotion.controller.platform.seckill.vo;
import lombok.Data;
import java.time.OffsetDateTime;

/** 平台运行概览白名单，不返回发布操作号、运行许可或完整快照。 */
@Data
public class PlatformSeckillRowRespVO {
    private Long id;
    private Long tenantId;
    private String name;
    private String productName;
    private Integer status;
    private Integer generation;
    private Long version;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
