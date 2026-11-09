package cn.qinghe.mall.module.trade.dal.dataobject.order;

import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 择取原TradeOrderDO普通快递订单字段；整数分用Long，不带已排除的积分/拼团/自提。
 * id沿用准备编号，不软删除财务业务记录；不将含收件信息的对象写日志。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class TradeOrderDO {
    private Long id;
    private Long tenantId;
    private String no;
    private Integer type;
    private Long userId;
    private String userRemark;
    private Integer status;
    private Long authVersion;
    private Integer productCount;
    private Long totalPrice;
    private Long discountPrice;
    private Long deliveryPrice;
    private Long payPrice;
    private Long couponId;
    private Long payOrderId;
    private Boolean payStatus;
    private LocalDateTime payTime;
    private LocalDateTime payDeadline;
    private Long refundPrice;
    /** 已确认退回的原运费；只由可信退款确认事务维护。 */
    private Long deliveryRefundPrice;
    /** 已准备且未确认结果的原运费占用，不能与原单位运费分摊混同。 */
    private Long deliveryRefundReserved;
    private Integer cancelType;
    private LocalDateTime cancelTime;
    /** 取消状态已提交，不等于跨服务库存/券已释放；仅在两侧回执核对后更新。 */
    private Boolean cancelResourcesReleased;
    private Long logisticsId;
    private String logisticsCode;
    private String logisticsName;
    private String logisticsNo;
    private LocalDateTime deliveryTime;
    private LocalDateTime receiveTime;
    /** 后续售后受理/结束须在本订单父锁内维护；非从前端传入。 */
    private Integer afterSalePendingCount;
    private String receiverName;
    private String receiverMobile;
    private Integer receiverAreaId;
    private String receiverDetailAddress;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
