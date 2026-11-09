package cn.qinghe.mall.module.system.dal.dataobject.tenant;

import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Set;

/** 平台统一维护的租户套餐，不按商家租户隔离。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
@TableName(value = "system_tenant_package", autoResultMap = true)
@TenantIgnore(enable = "true")
public class TenantPackageDO extends BaseDo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 0开启，1停用；停用只禁止新开通或切入。 */
    private Integer status;

    private String remark;

    /** 套餐允许的菜单资源集合，员工权限不能超出该范围。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> menuIds;

    /** 套餐授权资源版本，后续由变更流程维护。 */
    private Long authVersion;
}
