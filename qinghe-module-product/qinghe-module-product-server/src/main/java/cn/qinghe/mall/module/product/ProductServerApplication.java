package cn.qinghe.mall.module.product;

import cn.qinghe.mall.module.product.framework.file.ProductFileFeignApi;
import cn.qinghe.mall.module.product.framework.delivery.ProductDeliveryFeignApi;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableFeignClients(clients = {ProductFileFeignApi.class,ProductDeliveryFeignApi.class,
        cn.qinghe.mall.module.product.framework.storefront.ProductStorefrontApi.class})
@EnableConfigurationProperties({ProductSkuProperties.class,cn.qinghe.mall.module.product.framework.export.ProductExportProperties.class})
public class ProductServerApplication {
    public static void main(String[] args) { SpringApplication.run(ProductServerApplication.class, args); }
}
