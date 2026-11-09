package cn.qinghe.mall.module.trade.service.seckill.dto;

import cn.qinghe.mall.module.member.api.address.dto.MemberAddressRespDTO;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO;
import cn.qinghe.mall.module.trade.service.delivery.bo.TradeDeliveryQuoteBO;
import lombok.Data;

/** 仅trade内加密保存；promotion和Outbox只持报价id/摘要。 @author HUIHUI */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class TradeSeckillQuoteSnapshot {
    private Integer schemaVersion;
    private SeckillCheckoutContextDTO activity;
    private ProductTradeSkuRespDTO product;
    private MemberAddressRespDTO address;
    private TradeDeliveryQuoteBO delivery;
    private Integer quantity;
    private Long goodsAmount;
    private Long shippingAmount;
    private Long payAmount;
}
