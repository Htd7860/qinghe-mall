package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import lombok.Data;

/** 原售后/成交商品白名单摘要；申请金额不是已退金额，无用户、凭据或地址字段。
 * @author 芋道源码
 */
@Data
public class AfterSaleSummaryRespVO {
    private Long id;
    private String no;
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    private Long skuId;
    private String spuName;
    private String picUrl;
    private Integer count;
    private Integer way;
    private Integer status;
    private Long authVersion;
    private Long goodsRefundPrice;
    private Long createdAt;
    private Long updatedAt;
    private Long returnDeadline;
    private Long serverNow;
    private Boolean overdue;
}
