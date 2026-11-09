package cn.qinghe.mall.module.system.controller.admin.auth.vo;

import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 刷新凭据仅放JSON正文，客户端与租户归属由服务端确定。 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class AuthRefreshReqVO {

    @NotBlank(message = "刷新令牌不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}", message = "刷新令牌格式不正确")
    private String refreshToken;
}
