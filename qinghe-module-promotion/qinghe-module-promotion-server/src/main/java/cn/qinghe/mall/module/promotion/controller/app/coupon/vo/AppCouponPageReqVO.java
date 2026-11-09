package cn.qinghe.mall.module.promotion.controller.app.coupon.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
/** 原本人券分页，限定消费者响应大小。 @author 芋道源码 */
@Data @EqualsAndHashCode(callSuper=true)
public class AppCouponPageReqVO extends PageParam {
    @Min(1) @Max(5) private Integer status;
    @Override @NotNull @Min(1) @Max(20) public Integer getPageSize(){return super.getPageSize();}
    @Override @NotNull @Min(1) @Max(10000) public Integer getPageNo(){return super.getPageNo();}
}
