package cn.qinghe.mall.module.trade.controller.app.aftersale.vo;

import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** 择取原售后申请的订单项/方式/原因。当前按购买数量申请，金额从原成交单位计算。
 * 凭证附件关联待真实文件所有权验证接入，不提前接受任意URL。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AppAfterSaleCreateReqVO {
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private String requestId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long orderId;
    @NotNull @Min(1) @Max(9007199254740991L) private Long orderItemId;
    @NotNull @Min(0) @Max(9007199254740991L) private Long expectedVersion;
    @NotNull @Min(1) @Max(9999) private Integer count;
    @NotNull private Integer way;
    @NotBlank @Size(max=200) private String applyReason;
    @NotNull @Size(max=500) private String applyDescription="";
}
