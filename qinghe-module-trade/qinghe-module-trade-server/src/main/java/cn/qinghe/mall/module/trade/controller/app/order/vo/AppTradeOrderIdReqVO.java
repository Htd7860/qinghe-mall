package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;
import javax.validation.constraints.*;

@Data
public class AppTradeOrderIdReqVO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long id;
}
