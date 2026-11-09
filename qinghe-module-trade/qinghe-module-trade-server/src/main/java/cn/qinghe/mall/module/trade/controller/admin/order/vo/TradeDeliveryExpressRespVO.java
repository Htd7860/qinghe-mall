package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;

/** 原全局启用承运商的发货选项，不返回配置维护/审计字段。 */
@Data
public class TradeDeliveryExpressRespVO {
    private Long id;
    private String code;
    private String name;
}
