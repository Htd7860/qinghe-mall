package cn.qinghe.mall.module.infra.api.file.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** 由已认证商品服务调用；不接收tenant、owner或公开性字段。 */
@Data
public class FileBusinessBindReqDTO {
    @NotBlank @Pattern(regexp = "CATEGORY|BRAND|SPU")
    private String resourceType;
    @NotNull @Min(1)
    private Long resourceId;
    @NotBlank @Size(max = 1024)
    private String url;
}
