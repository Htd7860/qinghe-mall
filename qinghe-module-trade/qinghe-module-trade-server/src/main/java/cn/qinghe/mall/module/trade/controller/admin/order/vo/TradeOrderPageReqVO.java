package cn.qinghe.mall.module.trade.controller.admin.order.vo;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

/** 择取原订单分页，当前仅普通快递订单的状态/原编号精确筛选。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true)
public class TradeOrderPageReqVO extends PageParam {
    @Min(1) @Max(9007199254740991L) private Long id;
    @Pattern(regexp="QH[0-9a-f]{32}") private String no;
    private Integer status;
}
