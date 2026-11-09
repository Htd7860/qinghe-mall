package cn.qinghe.mall.module.system.controller.admin.permission.vo.permission;
import cn.qinghe.mall.module.system.controller.admin.management.vo.VersionedIdReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
import java.util.Set;
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionAssignRoleDataScopeReqVO extends VersionedIdReqVO {
    @NotNull @Min(1) @Max(5) private Integer dataScope;
    @NotNull @Size(max = 1000) private Set<@NotNull @Min(1) Long> deptIds;
}
