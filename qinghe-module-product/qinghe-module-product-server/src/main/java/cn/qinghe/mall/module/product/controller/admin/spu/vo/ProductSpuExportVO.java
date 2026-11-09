package cn.qinghe.mall.module.product.controller.admin.spu.vo;
import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** 从yudao SPU响应选择本次导出列，显式注解白名单；无成本、详情HTML、联系方式或凭据。 */
@Data
@ExcelIgnoreUnannotated
public class ProductSpuExportVO {
    @ExcelProperty("商品编号") private Long id;
    @ExcelProperty("商品名称") private String name;
    @ExcelProperty("分类编号") private Long categoryId;
    @ExcelProperty("品牌编号") private Long brandId;
    @ExcelProperty("状态") private String status;
    @ExcelProperty("售价（元）") private BigDecimal price;
    @ExcelProperty("可售库存") private Integer stock;
    @ExcelProperty("销量") private Integer salesCount;
    @ExcelProperty("归属员工编号") private Long ownerUserId;
    @ExcelProperty("归属部门编号") private Long ownerDeptId;
    @ExcelProperty("创建时间") private LocalDateTime createTime;
}
