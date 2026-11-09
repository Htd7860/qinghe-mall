package cn.qinghe.mall.module.product.framework.export;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;
/** 有界同步导出；并发配额是本实例总量，不冒称跨实例调度。 */
@Data
@Validated
@ConfigurationProperties(prefix="qinghe.product.export")
public class ProductExportProperties {
    @Min(1) @Max(20000) private int maxRows=5000;
    @Min(65536) @Max(33554432) private int maxFileBytes=8388608;
    @Min(1) @Max(8) private int maxConcurrent=2;
}
