package cn.qinghe.mall.module.product.controller.admin.spu.vo;
import lombok.Data;
import javax.validation.constraints.*;
/** 明确转移入口：客户端只能选择员工并回传已观察版本，不能任意指定tenant/部门。 */
@Data
public class ProductSpuTransferReqVO {
    @NotNull @Min(1) private Long id;
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull @Min(1) private Long targetUserId;
    @NotNull @Min(0) private Long expectedOwnerVersion;
    @NotBlank @Size(max=512) private String reason;
}
