package cn.qinghe.mall.module.trade.controller.app.cart.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
import java.util.List;
@Data @EqualsAndHashCode(callSuper=true)
public class AppCartDeleteReqVO extends AppCartWriteReqVO {
    @NotEmpty @Size(max=200) private List<@NotNull @Min(1) Long> ids;
}
