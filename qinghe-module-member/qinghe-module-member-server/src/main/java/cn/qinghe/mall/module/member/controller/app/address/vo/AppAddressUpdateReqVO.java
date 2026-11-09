package cn.qinghe.mall.module.member.controller.app.address.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import javax.validation.constraints.*;

@Schema(description = "用户 APP - 用户收件地址更新 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
public class AppAddressUpdateReqVO extends AppAddressBaseVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "编号不能为空")
    @Min(1) private Long id;

    @NotNull @Min(0) private Long expectedVersion;

}
