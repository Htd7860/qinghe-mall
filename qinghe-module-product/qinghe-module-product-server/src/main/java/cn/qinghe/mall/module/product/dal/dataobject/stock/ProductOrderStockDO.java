package cn.qinghe.mall.module.product.dal.dataobject.stock;

import lombok.Data;
import java.time.LocalDateTime;

/** 订单库存结果，不复用员工手工调整的身份与业务键。 */
@Data
public class ProductOrderStockDO {
    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long userId;
    private String requestHash;
    private String requestSnapshot;
    private Integer status;
    private String releaseReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
