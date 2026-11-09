package cn.qinghe.mall.module.system.framework.security;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;
/** 默认只允许已登记/已验证入口；编码定位仅显式配置回环开发Host。 */
@Data
@ConfigurationProperties(prefix="qinghe.storefront")
public class StorefrontProperties {
    private boolean allowLocalCodeLookup=false;
    private List<String> localCodeHosts=new ArrayList<>();
}
