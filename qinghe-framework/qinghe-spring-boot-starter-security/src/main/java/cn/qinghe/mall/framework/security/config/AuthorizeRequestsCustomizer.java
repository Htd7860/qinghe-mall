package cn.qinghe.mall.framework.security.config;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;

/** 模块在默认authenticated规则之前补充具体URL规则，参考yudao同名扩展点。 */
@FunctionalInterface
public interface AuthorizeRequestsCustomizer extends Customizer<
        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry> {
}
