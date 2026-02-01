package cn.qinghe.mall.framework.tenant.config;

import cn.qinghe.mall.framework.mybatis.config.QingheMybatisAutoConfiguration;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.config
 * @Project：qinghe-mall
 * @name：QingheTenantAutoConfiguration
 * @Date：2026/10/21 19:22
 * @Filename：QingheTenantAutoConfiguration
 */
@AutoConfiguration(before = MybatisPlusAutoConfiguration.class,after = QingheMybatisAutoConfiguration.class)
@EnableConfigurationProperties(TenantProperties.class)
@ConditionalOnProperty(prefix = "qinghe.tenant",name = "enable",havingValue = "true",matchIfMissing = true)
public class QingheTenantAutoConfiguration {
    @Bean
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties, MybatisPlusInterceptor interceptor){
        TenantLineInnerInterceptor tenantLineInnerInterceptor=new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
        MybatisUtils.addInterceptor(interceptor,tenantLineInnerInterceptor,0);
        return tenantLineInnerInterceptor;
    }
}
