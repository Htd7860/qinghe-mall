package cn.qinghe.mall.module.system.api.oauth2.dto;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 已由会员服务验证密码的候选身份，仅限内部服务调用；不接受client/type/scopes或密码。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class MemberTokenIssueReqDTO {
    @NotNull @Min(1) private Long tenantId;
    @NotNull @Min(1) private Long userId;
    @NotNull @Min(0) private Long credentialVersion;
}
