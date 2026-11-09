package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import lombok.Data;
import lombok.ToString;

/** 原日志分页投影；不返回操作者编号、UUID回执或含地址的原始details。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class AfterSaleLogRespVO {
    private Long id;
    private String actorType;
    private String action;
    private Integer fromStatus;
    private Integer toStatus;
    private Long afterSaleVersion;
    private Long createdAt;
    private String reason;
    private Boolean qualityApproved;
    private Inspection inspection;
    @Data
    public static class Inspection {
        private Integer receivedCount;
        private Integer salableCount;
        private Integer unsalableCount;
        private Boolean accepted;
    }
}
