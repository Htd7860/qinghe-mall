package cn.qinghe.mall.module.product.api.seckill.dto;

import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import lombok.Data;
import java.util.List;

/** 原 ProductSpuApi/ProductSkuApi 的活动校验快照；不输出成本、员工归属或条码。 */
@Data
public class SeckillProductSnapshotRespDTO {
    private Long tenantId;
    private Long spuId;
    private Long spuVersion;
    private String productName;
    private String picUrl;
    private List<Sku> skus;

    @Data
    public static class Sku {
        private Long skuId;
        private Long skuVersion;
        private Integer price;
        private List<ProductTradeSkuRespDTO.Property> properties;
    }
}
