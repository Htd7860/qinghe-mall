package cn.qinghe.mall.module.trade.controller.app.cart.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
import java.util.List;
/** 原ids/selected职责，版本覆盖整个购物车，全部归属校验后才更新。
 * @author 芋道源码
 */
@Data @EqualsAndHashCode(callSuper=true)
public class AppCartUpdateSelectedReqVO extends AppCartWriteReqVO {
    @NotEmpty @Size(max=200) private List<@NotNull @Min(1) Long> ids;
    @NotNull private Boolean selected;
}
