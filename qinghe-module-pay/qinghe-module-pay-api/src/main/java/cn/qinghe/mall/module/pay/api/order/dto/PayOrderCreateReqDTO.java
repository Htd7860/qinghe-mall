package cn.qinghe.mall.module.pay.api.order.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.time.LocalDateTime;

/** 择取原支付单创建字段；仅固定trade应用，金额由可信订单构造，不接浏览器金额/回调URL。
 * @author 芋道源码
 */
@Data
public class PayOrderCreateReqDTO {
    public static final int SUBJECT_MAX_LENGTH=32;
    @NotBlank @Pattern(regexp="qinghe-trade") private String appKey;
    @NotNull @Min(1) @Max(9007199254740991L) private Long userId;
    @NotNull private Integer userType;
    @NotBlank @Pattern(regexp="[1-9][0-9]{0,15}") private String merchantOrderId;
    @NotBlank @Size(max=SUBJECT_MAX_LENGTH) private String subject;
    @NotNull @Min(1) @Max(9007199254740991L) private Long price;
    @NotNull private LocalDateTime expireTime;
}
