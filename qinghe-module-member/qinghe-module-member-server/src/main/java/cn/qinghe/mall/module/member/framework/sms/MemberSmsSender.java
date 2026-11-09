package cn.qinghe.mall.module.member.framework.sms;
/** 取原SmsClient.sendSms职责；只允许当前两种验证码模板，不提供任意短信内容接口。 */
public interface MemberSmsSender {
    MemberSmsSendResult send(Long logId,Long tenantId,String mobile,MemberSmsScene scene,String code);
}
