package cn.qinghe.mall.module.product.dal.dataobject.spu;
import lombok.Data;
/** 仅可售SKU汇总；用Long承接SUM并在写回Integer库存前检查上限。 */
@Data
public class ProductSpuSkuSummary {
    private Long price;
    private Long marketPrice;
    private Long costPrice;
    private Long stock;
    private Long enabledCount;
}
