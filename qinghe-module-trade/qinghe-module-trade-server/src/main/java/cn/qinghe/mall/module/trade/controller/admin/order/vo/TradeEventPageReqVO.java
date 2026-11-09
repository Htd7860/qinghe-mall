package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

@Data @EqualsAndHashCode(callSuper=true)
public class TradeEventPageReqVO extends PageParam {
    @Pattern(regexp="PENDING|SENDING|PUBLISHED|MANUAL") private String status;
    @Pattern(regexp="ORDER_PREPARED|ORDER_DELIVERED|ORDER_RECEIVED|AFTERSALE_APPLIED|AFTERSALE_DECIDED|ORDER_EXPIRE_CHECK") private String eventType;
    @Min(1) @Max(9007199254740991L) private Long orderId;
    private Boolean consumed;
}
