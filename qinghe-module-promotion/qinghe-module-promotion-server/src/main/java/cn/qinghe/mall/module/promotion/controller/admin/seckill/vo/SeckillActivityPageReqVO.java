package cn.qinghe.mall.module.promotion.controller.admin.seckill.vo;

import cn.qinghe.mall.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

/** 原 SeckillActivityPageReqVO 的名称/状态分页职责；每页最多20，校验在 Service 再执行。 */
@Data
@EqualsAndHashCode(callSuper=true)
public class SeckillActivityPageReqVO extends PageParam {
    @Size(max=60)
    private String name;
    @Min(0) @Max(60)
    private Integer status;
}
