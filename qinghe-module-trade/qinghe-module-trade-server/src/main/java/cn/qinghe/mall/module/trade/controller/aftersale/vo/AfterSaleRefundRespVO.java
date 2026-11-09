package cn.qinghe.mall.module.trade.controller.aftersale.vo;

import lombok.Data;
import lombok.ToString;

/** 财务专用白名单：资金成功与资源完成分开，不返回原DO、渠道凭据或财务原因。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleRefundRespVO {
    private Long id;
    private Long orderId;
    private String refundNo;
    private String requestId;
    private Long preparedVersion;
    private Long authVersion;
    private String status;
    private Long goodsPrice;
    private Long deliveryPrice;
    private Long totalPrice;
    private Integer restoreCount;
    private Boolean stockRecovered;
    private Boolean couponReturnRequired;
    private Boolean couponRecovered;
    private Long confirmedAt;
    private Long updatedAt;
}
