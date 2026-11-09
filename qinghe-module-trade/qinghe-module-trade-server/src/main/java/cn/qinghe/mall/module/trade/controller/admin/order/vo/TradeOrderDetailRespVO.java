package cn.qinghe.mall.module.trade.controller.admin.order.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 普通查询只有脱敏收件信息；原地址/自由备注不在此投影。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true)
public class TradeOrderDetailRespVO extends TradeOrderPageItemRespVO {
    private String receiverNameMasked;
    private String receiverMobileMasked;
    private String receiverAreaName;
}
