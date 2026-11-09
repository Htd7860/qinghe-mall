package cn.qinghe.mall.module.trade.service.delivery.bo;
import lombok.Data;
import java.math.BigDecimal;
/** 服务端已核对的SKU/模板/优惠后实付款；不是消费者自由提交的价格接口。 */
@Data
public class TradeDeliveryLineBO {
    private Long skuId;
    private Long templateId;
    private Integer quantity;
    private BigDecimal unitWeight;
    private Long paidPrice;
}
