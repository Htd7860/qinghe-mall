package cn.qinghe.mall.module.member.framework.sms;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Data
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties(prefix="qinghe.member.sms")
public class MemberSmsProperties {
    private String mode="DISABLED";
    @JsonIgnore private String apiKey="";
    @JsonIgnore private String apiSecret="";
    private String signature="";
    private String registerTemplate="";
    private String resetPasswordTemplate="";
}
