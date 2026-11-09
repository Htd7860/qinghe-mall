package cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 快递运费模板分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeliveryExpressTemplatePageReqVO extends PageParam {

    @Schema(description = "模板名称", example = "王五")
    @javax.validation.constraints.Size(max=100)
    private String name;

    @Schema(description = "配送计费方式 1:按件 2:按重量")
    @javax.validation.constraints.Min(1) @javax.validation.constraints.Max(2)
    private Integer chargeMode;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @javax.validation.constraints.Size(max=2)
    private LocalDateTime[] createTime;

    @javax.validation.constraints.Min(0) @javax.validation.constraints.Max(1)
    private Integer status;
}
