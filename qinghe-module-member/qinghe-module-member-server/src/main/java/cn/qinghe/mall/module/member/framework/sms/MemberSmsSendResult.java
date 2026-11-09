package cn.qinghe.mall.module.member.framework.sms;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
/** 渠道受理结果，不表示手机已收到。无验证码、手机号或渠道密钥字段。 */
@Getter
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded=true)
public class MemberSmsSendResult {
    private final String provider;
    private final String requestId;
    private final String serialNo;
}
