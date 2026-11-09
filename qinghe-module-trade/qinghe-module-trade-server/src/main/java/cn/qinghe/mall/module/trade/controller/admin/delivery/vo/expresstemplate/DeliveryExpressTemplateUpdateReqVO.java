package cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

@Schema(description = "管理后台 - 快递运费模板更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeliveryExpressTemplateUpdateReqVO extends DeliveryExpressTemplateBaseVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "371")
    @NotNull(message = "编号不能为空")
    @Min(1)
    private Long id;

    @NotNull @Min(0) private Long expectedVersion;

    @Schema(description = "区域运费列表")
    @Valid
    @NotEmpty @Size(max=200)
    private List<@NotNull DeliveryExpressTemplateChargeBaseVO> charges;

    @Schema(description = "包邮区域列表")
    @Valid
    @NotNull @Size(max=200)
    private List<@NotNull DeliveryExpressTemplateFreeBaseVO> frees;

}
