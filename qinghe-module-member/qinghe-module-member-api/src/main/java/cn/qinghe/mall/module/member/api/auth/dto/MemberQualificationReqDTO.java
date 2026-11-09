package cn.qinghe.mall.module.member.api.auth.dto;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;
/** 仅由system根据服务端持久Token提供，不接受浏览器直接调用。无密码/手机号/原Token字段。 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class MemberQualificationReqDTO {
    @NotNull @Min(1) private Long tenantId;
    @NotNull @Min(1) private Long userId;
    @Min(0) private Long credentialVersion;
}
