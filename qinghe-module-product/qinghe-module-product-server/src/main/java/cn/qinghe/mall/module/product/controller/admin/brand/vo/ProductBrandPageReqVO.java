package cn.qinghe.mall.module.product.controller.admin.brand.vo;

import javax.validation.constraints.Size;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 商品品牌分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductBrandPageReqVO extends PageParam {

    @Schema(description = "品牌名称", example = "苹果")
    @Size(max = 100)
    private String name;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    @Size(max = 2)
    private LocalDateTime[] createTime;

}
