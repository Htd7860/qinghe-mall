package cn.qinghe.mall.module.system.controller.admin.tenant.vo.packages;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
@Data @EqualsAndHashCode(callSuper=true)
public class PackagePageReqVO extends PageParam {
    @Size(max=30) private String name;
    @Min(0) @Max(1) private Integer status;
    @NotNull private Boolean deleted=false;
}
