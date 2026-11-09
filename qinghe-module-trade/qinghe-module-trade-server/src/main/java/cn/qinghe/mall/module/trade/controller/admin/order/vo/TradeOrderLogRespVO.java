package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOrderDeliveryLogDTO;
import lombok.Data;
import lombok.ToString;

/** 原订单业务操作历史，不返回收件信息、令牌、事件payload或内部恢复错误。 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeOrderLogRespVO {
    private Long id;
    private Long actorId;
    private String actorType;
    private String action;
    private Integer fromStatus;
    private Integer toStatus;
    private Long orderVersion;
    private Long createdAt;
    private TradeOrderDeliveryLogDTO details;
}
