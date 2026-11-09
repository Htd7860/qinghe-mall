package cn.qinghe.mall.module.trade.controller.app.cart.vo;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class AppCartWriteReqVO {
    @NotNull @Min(0) @Max(9007199254740990L) private Long expectedVersion;
}
