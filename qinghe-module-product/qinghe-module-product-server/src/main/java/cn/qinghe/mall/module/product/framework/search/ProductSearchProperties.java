package cn.qinghe.mall.module.product.framework.search;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** 仅product内显式启用，不在启动期建索引/导入或连接外部服务。 */
@Data @ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties("qinghe.product.search")
public class ProductSearchProperties {
    private boolean enabled=false;
    private String endpoint="http://127.0.0.1:9200";
    private String index="qinghe-product-v1";
    @JsonIgnore private String apiKey="";
}
