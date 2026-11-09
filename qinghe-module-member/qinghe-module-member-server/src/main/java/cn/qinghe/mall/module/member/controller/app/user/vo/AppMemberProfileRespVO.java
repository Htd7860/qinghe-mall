package cn.qinghe.mall.module.member.controller.app.user.vo;
import lombok.Data;
import lombok.ToString;
/** 择取原AppMemberUserInfoRespVO本人资料；不含密码、IP、Token、后台权限或虚构交易统计。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class AppMemberProfileRespVO {
    private Long id;
    private String nickname;
    private String mobileMasked;
    private Boolean marketingOptOut;
    private Long authVersion;
}
