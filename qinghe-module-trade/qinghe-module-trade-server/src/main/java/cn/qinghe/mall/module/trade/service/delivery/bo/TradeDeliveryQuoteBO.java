package cn.qinghe.mall.module.trade.service.delivery.bo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.List;
/** 订单后续须持久化所用费率/区域/计量与分摊快照，不能事后查询新配置重算旧运费。 */
@Data
public class TradeDeliveryQuoteBO {
    private Long deliveryPrice;
    private List<Group> groups;
    @Data @EqualsAndHashCode
    public static class Group {
        private Long templateId;
        private Long templateVersion;
        private Integer chargeMode;
        private Integer areaId;
        private Long chargeRuleId;
        private Boolean chargeDefault;
        private List<Integer> chargeAreas;
        private BigDecimal startCount;
        private Integer startPrice;
        private BigDecimal extraCount;
        private Integer extraPrice;
        private Long freeRuleId;
        private Boolean freeDefault;
        private List<Integer> freeAreas;
        private Integer freePrice;
        private Integer freeCount;
        private Boolean freeApplied;
        private Long quantity;
        private BigDecimal chargeValue;
        private Long paidPrice;
        private Long deliveryPrice;
        private List<LineFee> lineFees;
        // MySQL JSON can normalize 1.000000 to 1.0; snapshot equality must compare numeric values.
        @EqualsAndHashCode.Include(replaces="startCount")
        BigDecimal comparableStartCount(){return canonical(startCount);}
        @EqualsAndHashCode.Include(replaces="extraCount")
        BigDecimal comparableExtraCount(){return canonical(extraCount);}
        @EqualsAndHashCode.Include(replaces="chargeValue")
        BigDecimal comparableChargeValue(){return canonical(chargeValue);}
        private static BigDecimal canonical(BigDecimal value){return value==null?null:value.stripTrailingZeros();}
    }
    @Data
    public static class LineFee {
        private Long skuId;
        private Long deliveryPrice;
    }
}
