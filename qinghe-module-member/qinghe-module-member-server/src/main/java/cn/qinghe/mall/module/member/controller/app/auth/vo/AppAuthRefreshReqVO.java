package cn.qinghe.mall.module.member.controller.app.auth.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 原refreshToken参数按当前JSON契约适配，凭据不放URL/响应/toString。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class AppAuthRefreshReqVO {
    @NotBlank @Pattern(regexp="rt_[A-Za-z0-9_-]{43}")
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY)
    private String refreshToken;
}
