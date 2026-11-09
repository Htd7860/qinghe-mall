package cn.qinghe.mall.module.promotion.api.seckill.dto;
import lombok.Data;
@Data
public class SeckillResourceLookupDTO {
    private String requestNo;
    private Long memberId;
    private Long orderId;
    private String requestDigest;
}
