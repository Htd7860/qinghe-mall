package cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages;
import cn.qinghe.mall.module.system.controller.admin.management.vo.VersionedIdReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
import java.util.Set;
@Data @EqualsAndHashCode(callSuper=true)
public class PackagePreviewReqVO extends VersionedIdReqVO {
    @NotNull @Size(max=10000) private Set<@NotNull @Min(1) Long> menuIds;
}
