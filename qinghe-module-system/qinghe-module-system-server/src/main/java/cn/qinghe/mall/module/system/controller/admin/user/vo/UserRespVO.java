package cn.qinghe.mall.module.system.controller.admin.user.vo;
import cn.qinghe.mall.module.system.dal.dataobject.user.AdminUserDO;
import lombok.Data;
import java.time.LocalDateTime;
/** 显式白名单，不返回password/credentialVersion/登录IP等字段。 */
@Data
public class UserRespVO {
    private Long id; private String username; private String nickname;
    private Long deptId; private Integer status; private Long authVersion;
    private String remark; private String email; private String mobile; private Integer sex;
    private Boolean deleted; private LocalDateTime createTime;
    public static UserRespVO from(AdminUserDO user) {
        UserRespVO out = new UserRespVO();
        out.setId(user.getId()); out.setUsername(user.getUsername()); out.setNickname(user.getNickname());
        out.setDeptId(user.getDeptId()); out.setStatus(user.getStatus()); out.setAuthVersion(user.getAuthVersion());
        out.setRemark(user.getRemark()); out.setEmail(user.getEmail()); out.setMobile(user.getMobile());
        out.setSex(user.getSex()); out.setDeleted(user.getDeleted()); out.setCreateTime(user.getCreateTime());
        return out;
    }
}
