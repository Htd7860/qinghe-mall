package cn.qinghe.mall.module.system.controller.admin.permission.vo.menu;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class MenuSaveReqVO {
    @Min(1) private Long id;
    @Min(0) @Max(9223372036854775806L) private Long expectedVersion;
    @NotNull @Pattern(regexp="merchant|platform") private String realm;
    @NotBlank @Size(max=50) private String name;
    @NotNull @Pattern(regexp="^$|[A-Za-z][A-Za-z0-9:_-]{0,99}") private String permission;
    @NotNull @Min(1) @Max(3) private Integer type;
    @NotNull @Min(0) @Max(10000) private Integer sort;
    @NotNull @Min(0) private Long parentId;
    @NotNull @Pattern(regexp="[A-Za-z0-9/_-]{0,200}") private String path;
    @NotNull @Pattern(regexp="[A-Za-z0-9:_-]{0,100}") private String icon;
    @NotNull @Pattern(regexp="[A-Za-z0-9/_-]{0,255}") private String component;
    @NotNull @Pattern(regexp="[A-Za-z0-9_]{0,255}") private String componentName;
    @NotNull @Min(0) @Max(1) private Integer status;
    @NotNull private Boolean visible;
    @NotNull private Boolean keepAlive;
    @NotNull private Boolean alwaysShow;
}
