package cn.qinghe.mall.module.product.dal.dataobject.stock;
import lombok.Data;
import java.time.LocalDateTime;
/** 单SKU库存前后值，不可修改；init_sku_id为数据库生成的首次入账唯一键。 */
@Data
public class ProductStockRecordDO {
    private Long id;
    private Long tenantId;
    private Long operationId;
    private Long spuId;
    private Long skuId;
    private String kind;
    private Integer delta;
    private Integer beforeStock;
    private Integer afterStock;
    private LocalDateTime createTime;
}
