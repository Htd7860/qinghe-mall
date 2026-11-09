package cn.qinghe.mall.module.promotion.controller.app.seckill.vo;
import lombok.Data;
import javax.validation.constraints.*;

/** 只接受原报价引用及客户端幂等号，身份/金额/库存数量从原报价取得。 */
@Data
public class AppSeckillAdmissionReqVO {
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String clientRequestId;
    @NotNull @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String quoteId;
    @NotNull @Pattern(regexp="[0-9a-f]{64}") private String quoteDigest;
}
