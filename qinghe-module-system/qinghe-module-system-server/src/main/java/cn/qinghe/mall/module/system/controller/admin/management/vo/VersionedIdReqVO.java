package cn.qinghe.mall.module.system.controller.admin.management.vo;

import lombok.Data;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class VersionedIdReqVO {
    @NotNull @Min(1)
    private Long id;
    @NotNull @Min(0) @Max(Long.MAX_VALUE - 1)
    private Long expectedVersion;
}
