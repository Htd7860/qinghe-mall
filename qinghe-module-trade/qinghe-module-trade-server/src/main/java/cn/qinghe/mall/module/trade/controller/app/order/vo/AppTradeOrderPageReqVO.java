package cn.qinghe.mall.module.trade.controller.app.order.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

/** 择取原AppTradeOrderPageReqVO的状态分页；会员与店铺由真实身份确定。 */
@Data @EqualsAndHashCode(callSuper=true)
public class AppTradeOrderPageReqVO extends PageParam {
    @Min(0) @Max(40) private Integer status;
}
