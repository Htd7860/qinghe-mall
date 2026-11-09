package cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.*;
import java.util.List;

/**
 * 快递运费模板包邮 Base VO，提供给添加运费模板使用
 */
@Data
public class DeliveryExpressTemplateFreeBaseVO {

    @Schema(description = "区域编号列表，默认规则传空数组", requiredMode = Schema.RequiredMode.REQUIRED, example = "[110000,120000]")
    @NotNull @Size(max=500)
    private List<@NotNull @Min(1) Integer> areaIds;

    @Schema(description = "包邮金额", requiredMode = Schema.RequiredMode.REQUIRED, example = "5000")
    @NotNull(message = "包邮金额不能为空")
    @Min(0)
    private Integer freePrice;

    @Schema(description = "包邮件数", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    @NotNull(message = "包邮件数不能为空")
    @Min(0)
    private Integer freeCount;

    @Min(1) private Long id;
    @NotNull private Boolean defaultRule = false;

}
