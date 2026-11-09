package cn.qinghe.mall.module.promotion.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;

/** 原epoch内保留原请求和拒绝墓碑，容量满只拒绝新记录，不驱逐仍有效的凭证。 */
@Data @Validated @ConfigurationProperties(prefix="qinghe.promotion.seckill-admission")
public class SeckillAdmissionProperties {
    @Min(1) @Max(10000) private int maxMembers=10000;
    @Min(1) @Max(30000) private int maxRequests=30000;
    @Min(1) @Max(10000) private int maxPendingEvents=1000;
}
