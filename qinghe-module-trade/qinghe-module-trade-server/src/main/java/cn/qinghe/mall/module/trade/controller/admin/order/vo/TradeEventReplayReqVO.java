package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeEventReplayReqVO {
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String eventId;
    @NotNull @Min(0) @Max(9007199254740990L) private Long expectedVersion;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
    @NotBlank @Size(max=200) private String reason;
}
