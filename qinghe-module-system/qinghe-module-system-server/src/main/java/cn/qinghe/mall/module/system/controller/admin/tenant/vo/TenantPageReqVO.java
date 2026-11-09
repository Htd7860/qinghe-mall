package cn.qinghe.mall.module.system.controller.admin.tenant.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
@Data @EqualsAndHashCode(callSuper=true)
public class TenantPageReqVO extends PageParam {
    @Size(max=32) private String code;
    @Size(max=128) private String name;
    @Min(0) @Max(1) private Integer status;
    @Min(1) private Long packageId;
    @NotNull private Boolean deleted=false;
}
