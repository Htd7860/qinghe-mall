package cn.qinghe.mall.module.system.controller.admin.user.vo;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;
@Data
@ToString(onlyExplicitlyIncluded = true)
public class UserSaveReqVO {
    @Min(1) private Long id;
    @Min(0) @Max(9223372036854775806L) private Long expectedVersion;
    @NotNull @Pattern(regexp="[A-Za-z0-9]{4,30}") private String username;
    @NotBlank @Size(max=30) private String nickname;
    @Size(max=100) private String password;
    @Min(1) private Long deptId;
    @NotNull @Min(0) @Max(1) private Integer status;
    @Size(max=500) private String remark;
    @Email @Size(max=50) private String email;
    @Pattern(regexp="^$|1[0-9]{10}") private String mobile;
    @Min(0) @Max(2) private Integer sex;
}
