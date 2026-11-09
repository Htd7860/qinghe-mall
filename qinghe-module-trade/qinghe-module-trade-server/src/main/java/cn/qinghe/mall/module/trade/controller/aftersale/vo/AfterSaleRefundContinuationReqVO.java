package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 当前财务一次原号授权依据；actor始终取服务端登录身份，不从浏览器接收。 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundContinuationReqVO extends AfterSaleRefundRetryReqVO {
    @NotNull @Min(0) @Max(9007199254740990L) private Long expectedPayVersion;
}
