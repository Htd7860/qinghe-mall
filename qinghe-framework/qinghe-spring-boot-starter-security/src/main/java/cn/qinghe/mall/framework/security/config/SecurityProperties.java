package cn.qinghe.mall.framework.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.security.config
 * @Project：qinghe-mall
 * @name：SecurityProperties
 * @Date：2026/10/22 20:49
 * @Filename：SecurityProperties
 */
@Data
@Validated
@ConfigurationProperties(prefix = "qinghe.security")
public class SecurityProperties {

    @NotBlank(message = "Token Header 不能为空")
    private String tokenHeader = "Authorization";
}
