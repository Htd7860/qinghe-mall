package cn.qinghe.mall.module.trade.dal.dataobject.order;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 择取原订单操作日志字段。详细更正原因仅从受保护查询返回，不写控制台。
 * @author 陈賝
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class TradeOrderLogDO {
    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long actorId;
    private String actorType;
    private String action;
    private Integer fromStatus;
    private Integer toStatus;
    private Long orderVersion;
    private String details;
    private LocalDateTime createTime;
}
