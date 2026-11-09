package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

@Data
@EqualsAndHashCode(callSuper=true)
public class SeckillPublishReasonReqVO extends SeckillPublishReqDTO {
    @NotBlank @Size(max=500)
    private String reason;
}
