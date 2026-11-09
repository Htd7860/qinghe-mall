package cn.qinghe.mall.module.system.api.oauth2.dto;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 持有原access即可请求清理其固定消费者会话，不恢复身份或接收目标user/tenant。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class MemberTokenLogoutReqDTO {
    @NotBlank @Pattern(regexp="at_[A-Za-z0-9_-]{43}") private String accessToken;
}
