package cn.qinghe.mall.module.pay.api.refund.dto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 仅可信trade财务适配构造。原号增加一次提交机会，不携带可修改金额或任意预算。 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class PayRefundContinuationReqDTO extends PayRefundGetReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long actorId;
    @NotNull @Min(0) @Max(9007199254740990L) private Long expectedVersion;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
    @NotBlank @Size(max=500) private String reason;
}
