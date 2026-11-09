package cn.qinghe.mall.module.promotion.service.seckill.dto;

import lombok.Data;
import javax.validation.constraints.*;

/** 商家发布意图，不接收库存、商品、租户或目标状态；超时重试必须使用原requestId。 */
@Data
public class SeckillPublishReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long id;
    @NotNull @Min(0) @Max(9007199254740990L)
    private Long expectedVersion;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private String requestId;
}
