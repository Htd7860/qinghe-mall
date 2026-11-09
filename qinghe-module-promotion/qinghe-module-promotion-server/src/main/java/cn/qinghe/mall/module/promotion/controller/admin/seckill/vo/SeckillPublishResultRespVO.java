package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import lombok.Data;
import java.util.List;

/** 本人原发布结果投影；历史代次不返回可误用的当前活动状态/版本，不暴露库存操作号。 */
@Data
public class SeckillPublishResultRespVO {
    private Long id;
    private String requestId;
    private Integer generation;
    private Long fromVersion;
    private Long preparedVersion;
    private Boolean currentGeneration;
    private Integer status;
    private Long version;
    private Boolean published;
    private Boolean withdrawn;
    private Boolean settled;
    private Long reopenedVersion;
    private Long serverTime;
    private List<Sku> skus;

    @Data
    public static class Sku {
        private Long skuId;
        private Integer quantity;
        private Integer productStatus;
        private Long productVersion;
        private Integer allocatedQuantity;
        private Integer returnedQuantity;
    }
}
