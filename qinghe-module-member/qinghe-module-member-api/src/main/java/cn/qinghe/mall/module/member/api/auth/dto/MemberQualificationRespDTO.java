package cn.qinghe.mall.module.member.api.auth.dto;
import lombok.Data;
/** 原会员API的最小资格投影，不返回手机号/密码/地址/角色。 */
@Data
public class MemberQualificationRespDTO {
    private Long tenantId;
    private Long userId;
    private String nickname;
    private Long credentialVersion;
}
