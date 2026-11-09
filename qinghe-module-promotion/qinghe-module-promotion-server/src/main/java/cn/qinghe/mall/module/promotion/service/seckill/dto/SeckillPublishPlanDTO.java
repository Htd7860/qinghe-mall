package cn.qinghe.mall.module.promotion.service.seckill.dto;

import cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationReqDTO;
import lombok.Data;
import java.util.List;

/** 仅服务器后续发布协调使用；preparedVersion不是已发布证明，规格业务号不直接暴露给消费者。 */
@Data
public class SeckillPublishPlanDTO {
    private Long activityId;
    private String requestId;
    private Integer generation;
    private Long preparedVersion;
    private Integer currentStatus;
    private Long currentVersion;
    private List<ProductSeckillAllocationReqDTO> allocations;
}
