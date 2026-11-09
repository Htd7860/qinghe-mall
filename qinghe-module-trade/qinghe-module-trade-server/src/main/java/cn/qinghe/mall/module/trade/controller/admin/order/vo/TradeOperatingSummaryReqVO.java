package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import javax.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

/** Asia/Shanghai本地时间，起始包含、结束不含；status只筛选订单当前状态。 */
@Data
public class TradeOperatingSummaryReqVO {
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private LocalDateTime beginTime;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) private LocalDateTime endTime;
    private Integer status;
}
