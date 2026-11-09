package cn.qinghe.mall.module.trade.api.seckill.dto;

import lombok.Data;

/** promotion可信协调器的原申请引用；不得直接绑定浏览器body，不收价格/地址/成功状态。 */
@Data @lombok.ToString(onlyExplicitlyIncluded=true)
public class SeckillQuoteBindingReqDTO {
    private Long memberId;
    private String requestNo;
    private String clientRequestId;
    private String requestDigest;
    private String quoteId;
    private String quoteDigest;
}
