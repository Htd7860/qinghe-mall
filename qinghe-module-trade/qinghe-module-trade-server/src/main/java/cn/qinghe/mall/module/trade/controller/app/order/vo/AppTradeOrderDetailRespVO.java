package cn.qinghe.mall.module.trade.controller.app.order.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 本人原订单快照，收件信息不跟随地址修改；不放入日志。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class AppTradeOrderDetailRespVO extends AppTradeOrderPageItemRespVO {
    private String userRemark;
    private String receiverName;
    private String receiverMobile;
    private Integer receiverAreaId;
    private String receiverAreaName;
    private String receiverDetailAddress;
}
