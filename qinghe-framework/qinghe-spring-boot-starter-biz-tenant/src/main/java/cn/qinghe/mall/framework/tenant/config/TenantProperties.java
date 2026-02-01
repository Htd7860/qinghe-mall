package cn.qinghe.mall.framework.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.config
 * @Project：qinghe-mall
 * @name：TenantProperties
 * @Date：2026/10/21 17:07
 * @Filename：TenantProperties
 */
@Data
@ConfigurationProperties(prefix = "qinghe.tenant")
public class TenantProperties {
    private Boolean enable=true;

    private Set<String> ignoreTables=new HashSet<>();
}
