package cn.qinghe.mall.module.member.controller.app.auth.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 原AppAuthLoginReqVO的手机号/密码；tenant仅为店铺编码提示，真实目标按Host解析。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class AppAuthLoginReqVO {
    @Pattern(regexp="[a-z0-9-]{3,32}") private String tenant;
    @NotBlank @Pattern(regexp="^1[3-9][0-9]{9}$") private String mobile;
    @NotEmpty @Size(max=72) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) private String password;
}
