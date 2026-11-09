package cn.qinghe.mall.module.pay.dal.dataobject.channel;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
/** 择取原PayChannelDO，私钥留在外部配置，数据库仅保存固定配置引用与收款身份。
 * @author 芋道源码
 */
@Data @ToString(onlyExplicitlyIncluded=true)
public class PayChannelDO {
    private Long id;
    private Long tenantId;
    private Long appId;
    private String code;
    private Integer status;
    private String configRef;
    private Long authVersion;
    private String alipayAppId;
    private String sellerId;
    private String notifyUrl;
}
