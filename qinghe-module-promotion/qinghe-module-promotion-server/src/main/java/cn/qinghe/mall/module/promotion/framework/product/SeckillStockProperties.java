package cn.qinghe.mall.module.promotion.framework.product;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 仅服务端配置，不从活动VO/请求头取凭据。空值禁止远程库存命令。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties(prefix="qinghe.promotion.seckill-stock")
public class SeckillStockProperties {
    @JsonIgnore
    private String serviceToken="";
}
