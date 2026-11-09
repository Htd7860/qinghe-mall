package cn.qinghe.mall.module.product.controller.app.spu.vo;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;
/** 择取yudao AppProductSpuPageReqVO关键词/分类/固定排序，增加本期品牌/起价区间。 @author 芋道源码 */
@Data @EqualsAndHashCode(callSuper=true)
public class AppProductSpuPageReqVO extends PageParam {
    @Size(max=64) private String keyword;
    @Min(1) @Max(9007199254740991L) private Long categoryId;
    @Min(1) @Max(9007199254740991L) private Long brandId;
    @Min(0) @Max(2000000000) private Integer minPrice;
    @Min(0) @Max(2000000000) private Integer maxPrice;
    @Pattern(regexp="price|salesCount|createTime") private String sortField;
    private Boolean sortAsc;
}
