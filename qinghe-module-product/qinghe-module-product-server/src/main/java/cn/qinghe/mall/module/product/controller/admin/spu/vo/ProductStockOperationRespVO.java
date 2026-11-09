package cn.qinghe.mall.module.product.controller.admin.spu.vo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
/** 白名单返回已提交库存操作，查不到不能推断仍在执行的事务一定失败。 */
@Data
public class ProductStockOperationRespVO {
    private Long id;
    private String operationKey;
    private String kind;
    private Long spuId;
    private LocalDateTime createTime;
    private List<Item> items;
    @Data
    public static class Item {
        private Long skuId;
        private Integer delta;
        private Integer beforeStock;
        private Integer afterStock;
    }
}
