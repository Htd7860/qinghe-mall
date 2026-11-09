package cn.qinghe.mall.module.system.dal.dataobject.tenant;

import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.dataobject.tenant
 * @Project：qinghe-mall
 * @name：TenantDO
 * @Date：2026/10/24 14:02
 * @Filename：TenantDO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
@TableName("system_tenant")
@TenantIgnore(enable = "true")
public class TenantDO extends BaseDo {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商家编码：全局唯一，创建后不变。 */
    private String code;

    /** 品牌显示名称，可修改、可重名。 */
    private String name;

    /** 租户管理员用户编号，创建管理员后关联。 */
    private Long contactUserId;

    /** 开通事务明确创建的管理员角色，不通过角色码推断特权。 */
    private Long adminRoleId;

    private String contactName;

    private String contactMobile;

    /** 运营状态：0开启，1关闭。 */
    private Integer status;

    /** 已分配的租户套餐编号。 */
    private Long packageId;

    /** 套餐到期时间。 */
    private LocalDateTime expireTime;

    /** 后台账号数量上限，包含管理员和禁用账号。 */
    private Integer accountCount;

    /** 授权状态版本，后续由 Service 按授权变化规则维护。 */
    private Long authVersion;
}
