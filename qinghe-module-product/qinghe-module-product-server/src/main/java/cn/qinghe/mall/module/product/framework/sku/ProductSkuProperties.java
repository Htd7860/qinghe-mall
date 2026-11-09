package cn.qinghe.mall.module.product.framework.sku;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/** 资源保护上限，可按实际商品规格规模配置；并非要求生成全部笛卡尔积。 */
@Data
@Validated
@ConfigurationProperties(prefix = "qinghe.product")
public class ProductSkuProperties {
    @Min(1) @Max(2000)
    private int maxSkus = 200;

    @Min(0) @Max(2147483647)
    private int alertStock = 10;
}
