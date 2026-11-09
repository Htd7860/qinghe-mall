package cn.qinghe.mall.module.member.controller.app.auth.vo;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 原AppAuthSmsSendReqVO按两个已实现用途收口，不开放客户端指定任意短信模板/scene编号。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class AppAuthSendCodeReqVO {
    @Pattern(regexp="[a-z0-9-]{3,32}") private String tenant;
    @NotBlank @Pattern(regexp="^1[3-9][0-9]{9}$") private String mobile;
    @NotBlank @Pattern(regexp="register|reset-password") private String purpose;
}
