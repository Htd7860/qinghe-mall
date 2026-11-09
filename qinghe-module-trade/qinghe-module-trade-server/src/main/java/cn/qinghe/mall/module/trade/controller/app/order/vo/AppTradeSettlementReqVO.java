package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;
import javax.validation.constraints.*;
/** 原settlement请求按购物车结算收敛为可信引用；不接user/tenant/单价/运费/优惠金额。
 * @author 芋道源码
 */
@Data
public class AppTradeSettlementReqVO {
    @NotNull @Min(0) @Max(9007199254740990L) private Long cartVersion;
    @NotNull @Min(1) @Max(9007199254740991L) private Long addressId;
    @Min(1) @Max(9007199254740991L) private Long couponId;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
}
