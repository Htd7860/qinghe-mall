package cn.qinghe.mall.module.product.dal.dataobject.seckill;

import lombok.Data;
import java.time.LocalDateTime;

/** 划拨/关闭凭证不逻辑删除，不继承含 deleted 列的 TenantBaseDo。 */
@Data
public class ProductSeckillAllocationDO {
    private Long id;
    private Long tenantId;
    private String activityNo;
    private Integer generation;
    private Long spuId;
    private Long skuId;
    private Long spuVersion;
    private Long skuVersion;
    private String operationNo;
    private String payloadHash;
    private Integer quantity;
    private Integer allocatedQuantity;
    private Integer returnedQuantity;
    private Integer status;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
