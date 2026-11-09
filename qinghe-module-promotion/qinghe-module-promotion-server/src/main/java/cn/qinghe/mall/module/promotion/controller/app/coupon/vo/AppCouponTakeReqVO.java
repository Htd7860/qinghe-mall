package cn.qinghe.mall.module.promotion.controller.app.coupon.vo;
import lombok.Data;
import javax.validation.constraints.*;
/** 原templateId请求，增加稳定请求号；不接受tenant/user/领取类型。 @author 芋道源码 */
@Data
public class AppCouponTakeReqVO {
    @NotNull @Min(1) @Max(9007199254740991L) private Long templateId;
    @NotBlank @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
}
