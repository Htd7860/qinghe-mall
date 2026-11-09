package cn.qinghe.mall.module.system.dal.dataobject.dept;
import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_dept")
public class DeptDO extends TenantBaseDo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
    private Integer status;
    private Long authVersion;
}
