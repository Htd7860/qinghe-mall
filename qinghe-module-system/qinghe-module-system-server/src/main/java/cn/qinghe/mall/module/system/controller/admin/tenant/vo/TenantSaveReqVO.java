package cn.qinghe.mall.module.system.controller.admin.tenant.vo;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;
import java.time.LocalDateTime;
@Data @ToString(onlyExplicitlyIncluded=true)
public class TenantSaveReqVO {
    @Min(1) private Long id;
    @Min(0) @Max(9223372036854775806L) private Long expectedVersion;
    @NotNull @Pattern(regexp="[a-z0-9][a-z0-9-]{2,31}") private String code;
    @NotBlank @Size(max=128) private String name;
    @Size(max=64) private String contactName;
    @Size(max=32) private String contactMobile;
    @NotNull @Min(0) @Max(1) private Integer status;
    @NotNull @Min(1) private Long packageId;
    @NotNull private LocalDateTime expireTime;
    @NotNull @Min(1) @Max(1000000) private Integer accountCount;
    /** 仅开通时提供；更新不能更换指定管理员。 */
    @Pattern(regexp="[A-Za-z0-9]{4,30}") private String adminUsername;
    @Size(max=30) private String adminNickname;
    @Size(max=100) private String adminPassword;
}
