package cn.qinghe.mall.framework.common.biz.system.permission.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/** 仅检查当前Bearer主体；HTTP不接受待查询的userId/tenantId。 */
@Data
public class PermissionCheckReqDTO {
    @NotEmpty
    @Size(max = 100)
    private List<@NotBlank @Size(max = 128) String> permissions;
}
