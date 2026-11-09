package cn.qinghe.mall.module.trade.dal.dataobject.aftersale;
import lombok.Data;

/** 原单位占用历史；REFUNDED继续占用同一单位，RELEASED保留历史但不阻止新申请。 */
@Data
public class AfterSaleUnitDO {
    private Long tenantId;
    private Long afterSaleId;
    private Long orderId;
    private Long orderItemId;
    private Integer unitNo;
    private Long goodsPrice;
    private Long originalDeliveryShare;
    private String state;
}
