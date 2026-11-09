package cn.qinghe.mall.module.system.controller.admin.management.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class VersionedStatusReqVO extends VersionedIdReqVO {
    @NotNull @Min(0) @Max(1)
    private Integer status;
}
