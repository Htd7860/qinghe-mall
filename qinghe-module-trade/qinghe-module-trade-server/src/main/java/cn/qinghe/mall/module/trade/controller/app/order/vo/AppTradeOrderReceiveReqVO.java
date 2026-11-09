package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;
import javax.validation.constraints.*;

/** 当前本人确认原已发货订单；只带id及所见版本。 */
@Data
public class AppTradeOrderReceiveReqVO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long id;
    @NotNull @Min(0) @Max(9007199254740991L) private Long expectedVersion;
}
