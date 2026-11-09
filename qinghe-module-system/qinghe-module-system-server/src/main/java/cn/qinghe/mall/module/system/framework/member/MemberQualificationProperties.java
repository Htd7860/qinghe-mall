package cn.qinghe.mall.module.system.framework.member;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.*;
@Data
@ToString(onlyExplicitlyIncluded=true)
@Validated
@ConfigurationProperties(prefix="qinghe.member-auth")
public class MemberQualificationProperties {
    private String memberBaseUrl="http://127.0.0.1:8084";
    @JsonIgnore private String systemToken="";
    @Min(1) @Max(512) private int maxConcurrentCalls=32;
}
