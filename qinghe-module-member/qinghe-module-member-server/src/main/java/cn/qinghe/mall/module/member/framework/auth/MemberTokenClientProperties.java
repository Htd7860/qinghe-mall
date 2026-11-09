package cn.qinghe.mall.module.member.framework.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import java.net.URI;

@Data
@Validated
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties("qinghe.member-token")
public class MemberTokenClientProperties {
    private String systemBaseUrl="http://127.0.0.1:8080";
    @JsonIgnore private String serviceToken="";
    @Min(1) @Max(128) private int maxConcurrentCalls=16;

    /** 两个固定system认证RPC复用同一来源/服务凭据规则，不接受请求动态覆盖。 */
    public String validatedServiceToken() {
        URI uri=URI.create(systemBaseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("会员认证system地址配置不正确");
        }
        if ("http".equals(uri.getScheme()) && !("127.0.0.1".equals(uri.getHost())
                || "localhost".equals(uri.getHost()) || "[::1]".equals(uri.getHost()))) {
            throw new IllegalArgumentException("非回环会员认证地址必须使用HTTPS");
        }
        if (serviceToken==null || !serviceToken.isEmpty() && !serviceToken.matches("[A-Za-z0-9_-]{43,128}")) {
            throw new IllegalArgumentException("会员认证凭据配置格式不正确");
        }
        return serviceToken;
    }
}
