package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;
import lombok.Data;
import java.time.OffsetDateTime;

/** 原发布的数据库运行阶段；不输出运行token/摘要/定义或推断Redis此刻可用。 */
@Data
public class SeckillRuntimeRespVO {
    private Long id;
    private String requestId;
    private Integer generation;
    private Long epoch;
    private Integer stage;
    private OffsetDateTime initDeadline;
    private String cacheCloseState;
    private Long serverTime;
}
