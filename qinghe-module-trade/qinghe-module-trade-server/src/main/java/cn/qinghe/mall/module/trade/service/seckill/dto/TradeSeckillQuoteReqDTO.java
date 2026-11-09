package cn.qinghe.mall.module.trade.service.seckill.dto;

import lombok.Data;
import javax.validation.constraints.*;

/** 会员HTTP及Service输入；不提供价格、运费、身份、coupon或初始化token字段。 */
@Data
public class TradeSeckillQuoteReqDTO {
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long activityId;
    @NotNull @Min(1) private Integer generation;
    @NotNull @Min(1) @Max(9007199254740991L) private Long skuId;
    @NotNull @Min(1) @Max(9999) private Integer quantity;
    @NotNull @Min(1) @Max(9007199254740991L) private Long addressId;
}
