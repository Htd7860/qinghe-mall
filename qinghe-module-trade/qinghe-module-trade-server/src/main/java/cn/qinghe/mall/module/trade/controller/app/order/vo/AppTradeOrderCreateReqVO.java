package cn.qinghe.mall.module.trade.controller.app.order.vo;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/**
 * 择取原 AppTradeOrderCreateReqVO 的备注职责；商品/地址/券仅取服务端报价。
 * 创建HTTP仅接原请求/报价与备注；归属、金额和商品始终取服务端。
 *
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class AppTradeOrderCreateReqVO {
    @NotBlank
    @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private String requestId;
    @NotBlank
    @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private String quoteId;
    @Size(max=500)
    @Pattern(regexp="[^\\x00-\\x1f\\x7f]*")
    private String remark;
}
