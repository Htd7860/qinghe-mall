package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

/** 后台活动读模型；UTC 时间有明确偏移，不输出发布操作键或消费者记录。 */
@Data
public class SeckillActivityDraftRespVO {
    private Long id;
    private String activityNo;
    private String name;
    private Long productSpuId;
    private Long productSpuVersion;
    private String productName;
    private String picUrl;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer singleLimit;
    private Integer memberLimit;
    private Integer status;
    private Long version;
    private Integer generation;
    private Long serverTime;
    private List<Sku> skus;

    @Data
    public static class Sku {
        private Long skuId;
        private Long skuVersion;
        private List<ProductTradeSkuRespDTO.Property> properties;
        private Integer priceCent;
        private Integer plannedQuantity;
        private Integer allocatedQuantity;
        private Integer availableQuantity;
        private Integer heldQuantity;
        private Integer soldQuantity;
        private Integer returnedQuantity;
    }
}
