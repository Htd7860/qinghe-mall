package cn.qinghe.mall.module.member.dal.dataobject.user;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.time.LocalDateTime;
/** 会员用户DO，择取yudao真实账号/基本资料；不引入积分/等级/经验/分销。
 * 手机号按tenant形成独立身份，删除不释放身份；停用/改密代次用于后续真实MEMBER凭据检查。
 * @author 芋道源码
 */
@TableName("member_user")
@Data
@EqualsAndHashCode(callSuper=true)
@ToString(onlyExplicitlyIncluded=true)
public class MemberUserDO extends TenantBaseDo {
    @TableId(type=IdType.AUTO) private Long id;
    private String mobile;
    @JsonIgnore private String password;
    private Integer status;
    private String registerIp;
    private Integer registerTerminal;
    private String loginIp;
    private LocalDateTime loginDate;
    private String nickname;
    private String avatar;
    private Boolean marketingOptOut;
    private Long authVersion;
    private Long credentialVersion;
}
