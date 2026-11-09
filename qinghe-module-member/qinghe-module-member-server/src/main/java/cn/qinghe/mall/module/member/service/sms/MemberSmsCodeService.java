package cn.qinghe.mall.module.member.service.sms;
import cn.qinghe.mall.module.member.framework.sms.MemberSmsScene;
import java.util.function.Supplier;
/** 择取yudao send/validate/use职责。调用者必须已权威确认店铺，不能把校验成功当注册授权凭证。
 * @author 芋道源码
 */
public interface MemberSmsCodeService {
    Long sendSmsCode(Long tenantId,String mobile,MemberSmsScene scene);
    void validateSmsCode(Long tenantId,String mobile,MemberSmsScene scene,String code);
    /** 合法验证码消费与本地会员变更同事务；错误尝试另以正常返回提交，再向外抛401。 */
    <T> T useSmsCode(Long tenantId,String mobile,MemberSmsScene scene,String code,Supplier<T> mutation);
}
