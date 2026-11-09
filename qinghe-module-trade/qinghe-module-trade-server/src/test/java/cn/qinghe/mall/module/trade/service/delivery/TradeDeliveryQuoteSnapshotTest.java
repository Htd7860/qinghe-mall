package cn.qinghe.mall.module.trade.service.delivery;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.trade.service.delivery.bo.TradeDeliveryQuoteBO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/** DEC-076 regression: MySQL JSON numeric normalization must not invalidate an unchanged quote. */
class TradeDeliveryQuoteSnapshotTest {
    private TradeDeliveryQuoteBO.Group original(){
        TradeDeliveryQuoteBO.Group g=new TradeDeliveryQuoteBO.Group();g.setTemplateId(1L);g.setTemplateVersion(2L);
        g.setStartCount(new BigDecimal("1.000000"));g.setExtraCount(new BigDecimal("0.500000"));
        g.setChargeValue(new BigDecimal("3.000000"));g.setDeliveryPrice(500L);return g;
    }
    @Test void normalizedJsonRetainsNumericEqualityAndHashCode(){
        TradeDeliveryQuoteBO.Group g=original();
        TradeDeliveryQuoteBO.Group stored=JsonUtils.parseObject(JsonUtils.toJsonString(g)
                .replace("1.000000","1.0").replace("0.500000","0.5").replace("3.000000","3"),TradeDeliveryQuoteBO.Group.class);
        assertThat(stored).isEqualTo(g);assertThat(stored.hashCode()).isEqualTo(g.hashCode());
    }
    @Test void actualRateChangeStillInvalidatesSnapshot(){TradeDeliveryQuoteBO.Group a=original(),b=original();b.setExtraCount(new BigDecimal("0.6"));assertThat(b).isNotEqualTo(a);}
    @Test void originalTemplateVersionAndFeeRemainProtected(){
        TradeDeliveryQuoteBO.Group a=original(),b=original();b.setTemplateVersion(3L);assertThat(b).isNotEqualTo(a);
        b=original();b.setDeliveryPrice(501L);assertThat(b).isNotEqualTo(a);
    }
}
