package cn.qinghe.mall.module.product.api.trade.dto;
import lombok.Data;
import java.util.List;
/** 择取原ProductSkuRespDTO/SPU公开销售字段；不含成本、归属员工、条码和运营统计。
 * @author 芋道源码
 */
@Data
public class ProductTradeSkuRespDTO {
    private Long tenantId;
    private Long skuId;
    private Long spuId;
    private Long categoryId;
    private Long deliveryTemplateId;
    private Long spuVersion;
    private Long skuVersion;
    private String name;
    private String picUrl;
    private Integer price;
    private Integer stock;
    private String weight;
    private List<Property> properties;
    @Data public static class Property {
        private Long propertyId;
        private String propertyName;
        private Long valueId;
        private String valueName;
    }
}
