package cn.qinghe.mall.module.pay.api.order.dto;
import lombok.Data;
import java.time.LocalDateTime;
/** 择取原PayOrderRespDTO；内部返回身份/金额/期限供订单核对，不暴露渠道用户、密钥或任意回调地址。
 * @author 芋道源码
 */
@Data
public class PayOrderRespDTO {
    private Long id;
    private Long tenantId;
    private Long appId;
    private String appKey;
    private Long userId;
    private Integer userType;
    private String merchantOrderId;
    private String subject;
    private Long price;
    private Integer status;
    /** 已进入关单流程，不能继续提交付款；不等于渠道已经确认关闭。 */
    private Boolean closing;
    /** 迟到付款已持久登记，禁止把主账的旧CLOSED投影误作可以释放资源。 */
    private Boolean reviewRequired;
    private LocalDateTime expireTime;
    private LocalDateTime successTime;
    private Long refundPrice;
    private Long authVersion;
}
