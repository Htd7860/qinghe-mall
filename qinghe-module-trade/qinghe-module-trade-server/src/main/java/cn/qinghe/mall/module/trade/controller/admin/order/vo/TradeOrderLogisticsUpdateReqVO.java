package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.*;

/** 已发货单号更正，需独立权限/原因；不能修改首次发货时间或数量。 */
@Data @EqualsAndHashCode(callSuper=true) @ToString(onlyExplicitlyIncluded=true)
public class TradeOrderLogisticsUpdateReqVO extends TradeOrderDeliveryReqVO {
    @NotBlank @Size(max=500) private String reason;
}
