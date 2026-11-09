package cn.qinghe.mall.module.trade.controller.admin.delivery.vo.expresstemplate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.*;
import java.util.List;

/**
 * 快递运费模板运费设置 Base VO，提供给添加运费模板使用
 */
@Data
public class DeliveryExpressTemplateChargeBaseVO {

    @Schema(description = "编号", example = "6592", hidden = true) // 由于想简单一点，复用这个 VO 在更新操作，所以 hidden 为 false
    @Min(1)
    private Long id;

    @Schema(description = "区域编号列表，默认规则传空数组", requiredMode = Schema.RequiredMode.REQUIRED, example = "[110000,120000]")
    @NotNull @Size(max=500)
    private List<@NotNull @Min(1) Integer> areaIds;

    @Schema(description = "首件数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    @NotNull(message = "首件数量不能为空")
    @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private java.math.BigDecimal startCount;

    @Schema(description = "起步价", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "起步价不能为空")
    @Min(0)
    private Integer startPrice;

    @Schema(description = "续件数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "续件数量不能为空")
    @DecimalMin(value="0",inclusive=false) @Digits(integer=12,fraction=6)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private java.math.BigDecimal extraCount;

    @Schema(description = "额外价", requiredMode = Schema.RequiredMode.REQUIRED, example = "2000")
    @NotNull(message = "额外价不能为空")
    @Min(0)
    private Integer extraPrice;

    @NotNull private Boolean defaultRule = false;
}
