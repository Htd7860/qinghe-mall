package cn.qinghe.mall.module.system.framework.member;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@Validated
@ToString(onlyExplicitlyIncluded=true)
@ConfigurationProperties("qinghe.member-token-issue")
public class MemberTokenIssueProperties {
    /** member -> system签发专用，与system -> member资格凭据分开；空配置失败关闭。 */
    @JsonIgnore private String memberToken="";
    /** promotion只能解析商城入口；不得复用会员签发或资格凭据。 */
    @JsonIgnore private String promotionStorefrontToken="";
    /** product目录只读解析入口，与会员签发凭据分开。 */
    @JsonIgnore private String productStorefrontToken="";
    @Min(1) @Max(128) private int maxConcurrent=16;
}
