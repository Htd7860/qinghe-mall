package cn.qinghe.mall.module.trade.controller.aftersale.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

/** 择取原售后分页筛选；租户、会员与数据范围由服务端入口决定。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true)
public class AfterSalePageReqVO extends PageParam {
    @Min(1) @Max(9007199254740991L) private Long id;
    @Pattern(regexp="AS[0-9a-f]{32}") private String no;
    @Min(1) @Max(9007199254740991L) private Long orderId;
    private Integer status;
    private Integer way;
    @NotNull private Boolean overdueOnly=false;
}
