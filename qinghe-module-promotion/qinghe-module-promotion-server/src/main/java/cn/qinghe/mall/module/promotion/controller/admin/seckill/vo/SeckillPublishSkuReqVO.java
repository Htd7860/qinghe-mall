package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

/** 单次只处理原计划一个规格，不接受外部金额、数量、库存操作号或租户。 */
@Data
@EqualsAndHashCode(callSuper=true)
public class SeckillPublishSkuReqVO extends SeckillPublishReqDTO {
    @NotNull @Min(1) @Max(9007199254740991L)
    private Long skuId;
}
