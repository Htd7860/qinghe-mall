package cn.qinghe.mall.module.promotion.controller.platform.seckill.vo;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.SeckillActivityPageReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.validation.constraints.*;

/** 平台活动分页，可按商家筛选；筛选值不改变平台登录身份。 */
@Data @EqualsAndHashCode(callSuper=true)
public class PlatformSeckillPageReqVO extends SeckillActivityPageReqVO {
    @Min(1) @Max(9007199254740991L)
    private Long tenantId;
}
