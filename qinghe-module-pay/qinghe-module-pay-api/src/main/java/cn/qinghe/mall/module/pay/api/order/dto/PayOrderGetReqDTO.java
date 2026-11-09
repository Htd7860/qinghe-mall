package cn.qinghe.mall.module.pay.api.order.dto;

import lombok.Data;
import javax.validation.constraints.*;

/** 原业务键查询；查不到不能证明另一创建请求未在途。 */
@Data
public class PayOrderGetReqDTO {
    @NotBlank @Pattern(regexp="qinghe-trade") private String appKey;
    @NotNull @Min(1) @Max(9007199254740991L) private Long userId;
    @NotBlank @Pattern(regexp="[1-9][0-9]{0,15}") private String merchantOrderId;
}
