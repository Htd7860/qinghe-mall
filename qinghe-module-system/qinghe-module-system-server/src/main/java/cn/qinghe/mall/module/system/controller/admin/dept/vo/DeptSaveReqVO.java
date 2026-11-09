package cn.qinghe.mall.module.system.controller.admin.dept.vo;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class DeptSaveReqVO {
    @Min(1) private Long id;
    @Min(0) @Max(9223372036854775806L) private Long expectedVersion;
    @NotBlank @Size(max=30) private String name;
    @NotNull @Min(0) private Long parentId;
    @NotNull @Min(0) @Max(10000) private Integer sort;
    @NotNull @Min(0) @Max(1) private Integer status;
}
