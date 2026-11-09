package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 原售后详情职责；寄回联系人/街道单独按处理用途读取，不复制原始DO或日志details。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleDetailRespVO extends AfterSaleSummaryRespVO {
    private String applyReason;
    private String applyDescription;
    private Boolean qualityApproved;
    private String auditReason;
    private Long auditedAt;
    private Long deliveredAt;
    private Long inspectedAt;
    private Long logisticsId;
    private String logisticsName;
    private String logisticsNo;
    private Integer receivedCount;
    private Integer salableCount;
    private Integer unsalableCount;
    private String receiveReason;
    private Refund refund;
    /** 资金和资源分别展示；不暴露渠道或原财务原因/操作人。 */
    @Data @ToString(onlyExplicitlyIncluded=true)
    public static class Refund {
        private Long goodsPrice;
        private Long deliveryPrice;
        private Long totalPrice;
        private String status;
        private Long confirmedAt;
        private Integer restoreCount;
        private Boolean stockRecovered;
        private Boolean couponReturnRequired;
        private Boolean couponRecovered;
    }
}
