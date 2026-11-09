package cn.qinghe.mall.module.system.api.oauth2.dto;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/** 内部刷新：tenant必须来自member实际入口解析，用户/凭据版本只从持久refresh读取。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class MemberTokenRefreshReqDTO {
    @NotNull @Min(1) private Long tenantId;
    @NotBlank @Pattern(regexp="rt_[A-Za-z0-9_-]{43}") private String refreshToken;
}
