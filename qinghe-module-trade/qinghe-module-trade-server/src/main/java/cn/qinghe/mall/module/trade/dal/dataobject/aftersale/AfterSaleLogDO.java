package cn.qinghe.mall.module.trade.dal.dataobject.aftersale;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

/** 原售后操作日志职责，补原命令回执字段。原因不写控制台。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleLogDO {
    private Long id;
    private Long tenantId;
    private Long afterSaleId;
    private Long actorId;
    private String actorType;
    private String action;
    private Integer fromStatus;
    private Integer toStatus;
    private Long afterSaleVersion;
    private LocalDateTime createTime;
    private String requestId;
    private String reason;
    private Boolean qualityApproved;
    private String details;
}
