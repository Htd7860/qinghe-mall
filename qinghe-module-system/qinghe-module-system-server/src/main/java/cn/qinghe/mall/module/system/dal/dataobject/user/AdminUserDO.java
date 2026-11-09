package cn.qinghe.mall.module.system.dal.dataobject.user;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.dataobject.user
 * @Project：qinghe-mall
 * @name：AdminUserDo
 * @Date：2026/10/24 14:50
 * @Filename：AdminUserDo
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
@TableName(value = "system_users", autoResultMap = true)
public class AdminUserDO extends TenantBaseDo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，同租户内唯一。 */
    private String username;

    /** 密码编码结果，不存明文。 */
    private String password;

    private String nickname;

    private String remark;

    /** 所属部门。 */
    private Long deptId;

    /** 岗位编号集合。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> postIds;

    private String email;

    private String mobile;

    private Integer sex;

    private String avatar;

    /** 账号状态：0开启，1关闭。 */
    private Integer status;

    private String loginIp;

    private LocalDateTime loginDate;

    /** 账号授权状态版本，后续由 Service 维护。 */
    private Long authVersion;

    /** 持久会话代次；停用、删除、恢复和改密后递增，与角色配置版本分开。 */
    private Long credentialVersion;
}
