package cn.qinghe.mall.module.product.dal.dataobject.stock;
import lombok.Data;
import java.time.LocalDateTime;
/** 不可变成功操作；只经固定SQL写入，不提供软删除/任意更新。 */
@Data
public class ProductStockOperationDO {
    private Long id;
    private Long tenantId;
    private String operationKey;
    private String kind;
    private Long spuId;
    private String requestHash;
    private Long actorUserId;
    private String reason;
    private LocalDateTime createTime;
}
