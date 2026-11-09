package cn.qinghe.mall.module.pay.dal.dataobject.order;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
/** 择取原PayOrderExtensionDO渠道尝试；当前一单固定一次渠道身份，超时不换号。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class PayOrderExtensionDO {
    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long channelId;
    private Long channelVersion;
    private String no;
    private String state;
    private String displayContent;
    private String channelOrderNo;
    private LocalDateTime successTime;
    private Long authVersion;
    private Integer qrRecoveryCount;
    private LocalDateTime qrRecoveryAfter;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
