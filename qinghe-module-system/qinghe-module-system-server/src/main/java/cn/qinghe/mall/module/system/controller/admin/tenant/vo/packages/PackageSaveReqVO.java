package cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages;
import lombok.Data;
import javax.validation.constraints.*;
import java.util.Set;
@Data
public class PackageSaveReqVO {
    @Min(1) private Long id;
    @Min(0) @Max(9223372036854775806L) private Long expectedVersion;
    @NotBlank @Size(max=30) private String name;
    @NotNull @Min(0) @Max(1) private Integer status;
    @Size(max=256) private String remark;
    @NotNull @Size(max=10000) private Set<@NotNull @Min(1) Long> menuIds;
    /** 更新请求使用稳定UUID；同一请求重试不能换operationId。 */
    @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") private String operationId;
    @Pattern(regexp="[0-9a-f]{64}") private String impactToken;
}
