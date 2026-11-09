package cn.qinghe.mall.module.system.controller.admin.permission.vo.role;

import lombok.Data;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 创建/资料更新白名单；类型、归属和数据范围不能借此字段赋值。 */
@Data
public class RoleSaveReqVO {
    private Long id;
    private Long expectedVersion;
    @NotBlank @Size(max = 30)
    private String name;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9:_-]{1,100}")
    private String code;
    @NotNull @Min(0) @Max(10000)
    private Integer sort;
    @NotNull @Min(0) @Max(1)
    private Integer status;
    @Size(max = 500)
    private String remark;
}
