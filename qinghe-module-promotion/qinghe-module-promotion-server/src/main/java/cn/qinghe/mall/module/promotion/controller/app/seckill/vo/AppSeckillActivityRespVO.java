package cn.qinghe.mall.module.promotion.controller.app.seckill.vo;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

/** 本店已发布活动销售白名单；库存显示为本库快照，不承诺报价或受理成功。 */
@Data
public class AppSeckillActivityRespVO {
    private Long id;
    private String name;
    private Long spuId;
    private String productName;
    private String picUrl;
    private Integer generation;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer singleLimit;
    private Integer memberLimit;
    private Integer status;
    private Integer minPriceCent;
    private Boolean quoteAvailable;
    private Long serverTime;
    private List<Sku> skus;
    @Data public static class Sku {
        private Long skuId;
        private List<ProductTradeSkuRespDTO.Property> properties;
        private Integer priceCent;
        private Integer availableQuantity;
    }
}
