package cn.qinghe.mall.module.trade.controller.admin.order.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.Pattern;

/** 择取原订单分页VO职责；仅固定状态/动作过滤，无自由SQL/租户/用户输入。 */
@Data
@EqualsAndHashCode(callSuper=true)
public class TradeRecoveryPageReqVO extends PageParam {
    @Pattern(regexp="READY|RUNNING|DONE|MANUAL") private String status;
    @Pattern(regexp="PAY_SYNC|CANCEL_EXPIRED|CANCEL_RELEASE|CREATE_COMPENSATE|AUTO_RECEIVE|RETURN_EXPIRE|REFUND") private String kind;
}
