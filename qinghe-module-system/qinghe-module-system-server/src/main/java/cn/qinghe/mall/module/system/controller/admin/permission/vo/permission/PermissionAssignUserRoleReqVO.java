package cn.qinghe.mall.module.system.controller.admin.permission.vo.permission;
import lombok.Data;
import javax.validation.constraints.*;
import java.util.Set;
@Data
public class PermissionAssignUserRoleReqVO {
    @NotNull @Min(1) private Long userId;
    @NotNull @Min(0) @Max(9223372036854775806L) private Long expectedVersion;
    @NotNull @Size(max = 1000) private Set<@NotNull @Min(1) Long> roleIds;
}
