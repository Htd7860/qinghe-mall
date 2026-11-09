package cn.qinghe.mall.module.product.controller.admin.property.vo.property;

import javax.validation.constraints.Size;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 属性项 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductPropertyPageReqVO extends PageParam {

    @Schema(description = "名称", example = "颜色")
    @Size(max = 100)
    private String name;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    @Size(max = 2)
    private LocalDateTime[] createTime;

    private Integer type;

}
