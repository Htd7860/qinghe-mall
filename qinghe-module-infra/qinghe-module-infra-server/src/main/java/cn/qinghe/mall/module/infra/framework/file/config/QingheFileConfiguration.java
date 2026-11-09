package cn.qinghe.mall.module.infra.framework.file.config;

import cn.qinghe.mall.module.infra.framework.file.core.client.FileClientFactory;
import cn.qinghe.mall.module.infra.framework.file.core.client.FileClientFactoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 注册沿用 yudao 的文件客户端工厂；存储配置由后续文件配置服务提供。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileUploadProperties.class)
public class QingheFileConfiguration {

    @Bean
    public FileClientFactory fileClientFactory() {
        return new FileClientFactoryImpl();
    }
}
