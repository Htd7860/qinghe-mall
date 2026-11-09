package cn.qinghe.mall.module.trade.api.delivery.dto;
import lombok.Data;
/** 真实当前模板配置摘要；不等于具体地址已经可配送或已经算出运费。 */
@Data
public class DeliveryExpressTemplateCheckRespDTO {
    private Long id;
    private Long tenantId;
    private Long authVersion;
    private Integer chargeMode;
}
