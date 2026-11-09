package cn.qinghe.mall.module.member.controller.app.auth.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 择取原AppMemberUserResetPasswordReqVO的mobile/code/password；注册共用同一白名单。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class AppAuthAccountReqVO {
    @Pattern(regexp="[a-z0-9-]{3,32}") private String tenant;
    @NotBlank @Pattern(regexp="^1[3-9][0-9]{9}$") private String mobile;
    @NotBlank @Pattern(regexp="[0-9]{6}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) private String code;
    @NotEmpty @Size(max=72) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) private String password;
}
