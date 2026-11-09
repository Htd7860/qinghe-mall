package cn.qinghe.mall.module.trade.dal.dataobject.aftersale;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 择取原PayRefundDO的稳定退款标识/归属/金额/状态职责；本表是交易侧原退款计划，非渠道到账凭证。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundDO {
    private Long tenantId;
    private Long afterSaleId;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private Long payOrderId;
    private String requestId;
    private String refundNo;
    private Long actorId;
    /** 首次明确执行的财务身份与时间；仅准备计划不会触发后台付款。 */
    private Long executionActorId;
    private LocalDateTime executionRequestedAt;
    private String reason;
    private Long afterSaleVersion;
    private String status;
    private Long payRefundId;
    private Long payRefundVersion;
    private LocalDateTime refundTime;
    private Boolean stockRecovered;
    private Boolean couponRecovered;
    private String stockReceipt;
    private String couponReceipt;
    private Long goodsPrice;
    private Long deliveryPrice;
    private Long totalPrice;
    /** 仅计划，不能当作已恢复库存。 */
    private Integer restoreCount;
    /** 仅计划，实际仍须原券快照/状态核对，不延期恢复。 */
    private Boolean couponReturnRequired;
    private Boolean shipped;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
