package cn.qinghe.mall.module.member.framework.address;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;
/** 每会员有效地址资源上限；新增时在真实会员父锁内计数。 */
@Data
@Validated
@ConfigurationProperties(prefix="qinghe.member.address")
public class MemberAddressProperties {
    @Min(1) @Max(100) private int maxAddresses=20;
}
