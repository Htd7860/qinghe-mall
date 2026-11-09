package cn.qinghe.mall.module.pay.api.refund.dto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 只读原人工授权回执，不需要重传私密原因。actor只能来自可信trade当前身份。 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class PayRefundContinuationGetReqDTO extends PayRefundGetReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long actorId;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
}
