package cn.qinghe.mall.module.promotion.api.seckill.dto;
import lombok.Data;
import java.time.OffsetDateTime;
/** 原申请当前付款资格，只读观察；最终付款/关单竞争仍由支付永久关闭栅栏裁决。 */
@Data
public class SeckillPaymentEligibilityDTO {
    private Long tenantId;
    private String requestNo;
    private Long memberId;
    private Long orderId;
    private String requestDigest;
    private Long requestVersion;
    private OffsetDateTime payDeadline;
    private Boolean canPay;
}
