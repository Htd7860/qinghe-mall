package cn.qinghe.mall.module.promotion.controller.platform.seckill.vo;
import lombok.Data;
import javax.validation.constraints.*;

/** 原目标/代次/版本即命令身份；未知结果按同一组参数重试。 */
@Data
public class PlatformSeckillStopReqVO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long tenantId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long id;
    @NotNull @Min(1) private Integer generation;
    @NotNull @Min(0) @Max(9007199254740990L) private Long expectedVersion;
    @NotBlank @Size(max=500) private String reason;
}
