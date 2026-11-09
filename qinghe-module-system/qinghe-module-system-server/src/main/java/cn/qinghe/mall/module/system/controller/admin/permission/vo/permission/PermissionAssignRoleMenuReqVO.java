package cn.qinghe.mall.module.system.controller.admin.permission.vo.permission;

import lombok.Data;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Set;

@Data
public class PermissionAssignRoleMenuReqVO {
    @NotNull @Min(1)
    private Long roleId;
    @NotNull @Min(0) @Max(Long.MAX_VALUE - 1)
    private Long expectedVersion;
    @NotNull @Size(max = 10000)
    private Set<@NotNull @Min(1) Long> menuIds;
}
