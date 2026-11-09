package cn.qinghe.mall.module.system.controller.admin.auth.vo;

import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 后台密码登录；不接收外部指定的clientId、tenantId或scopes。 */
@Data
@ToString(onlyExplicitlyIncluded = true)
public class AuthLoginReqVO {

    /** 前端现有字段名；商家必填，平台不填写。 */
    @Pattern(regexp = "(?:[a-z0-9-]{3,32})?", message = "商家编码格式不正确")
    private String tenant;

    @NotBlank(message = "登录账号不能为空")
    @Pattern(regexp = "[A-Za-z0-9]{4,30}", message = "账号须为4至30位字母或数字")
    private String username;

    @NotEmpty(message = "密码不能为空")
    @Size(max = 72, message = "密码长度超过限制")
    private String password;
}
