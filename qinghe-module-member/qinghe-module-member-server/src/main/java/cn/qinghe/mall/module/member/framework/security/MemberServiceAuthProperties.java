package cn.qinghe.mall.module.member.framework.security;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Data
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties(prefix="qinghe.member.service-auth")
public class MemberServiceAuthProperties {
    /** 空值仅关闭这一个服务入口；不能提供开发默认密钥。 */
    @JsonIgnore private String systemToken="";
}
