package cn.qinghe.mall.module.member.framework.sms;
import java.time.Instant;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 只有sms-test显式profile+TEST模式+回环绑定可创建。验证码仅可Java测试读取，不写日志/文件/HTTP。 */
public final class TestMemberSmsOutbox implements MemberSmsSender {
    private static final int MAXIMUM=1000;
    private final Map<Long,Delivery> outbox=new LinkedHashMap<>();
    @Override
    public synchronized MemberSmsSendResult send(Long logId,Long tenantId,String mobile,MemberSmsScene scene,String code) {
        AliyunMemberSmsSender.validate(logId,tenantId,mobile,scene,code);
        Instant now=Instant.now();outbox.entrySet().removeIf(entry -> !entry.getValue().expires.isAfter(now));
        Delivery previous=outbox.get(logId);
        if (previous!=null) {
            if (!previous.tenant.equals(tenantId) || !previous.mobile.equals(mobile) || previous.scene!=scene || !previous.code.equals(code)) { throw exception0(409,"测试短信编号已绑定其他内容"); }
            return new MemberSmsSendResult("TEST",logId.toString(),logId.toString());
        }
        if (outbox.size()>=MAXIMUM) { throw exception0(503,"测试短信收件箱已满"); }
        outbox.put(logId,new Delivery(tenantId,mobile,scene,code,now.plusSeconds(300)));
        return new MemberSmsSendResult("TEST",logId.toString(),logId.toString());
    }
    public synchronized String readForTest(Long logId,Long tenantId,String mobile,MemberSmsScene scene) {
        Delivery value=outbox.get(logId);
        if (value==null || !value.expires.isAfter(Instant.now()) || !value.tenant.equals(tenantId) || !value.mobile.equals(mobile) || value.scene!=scene) { return null; }
        return value.code;
    }
    private static final class Delivery {
        private final Long tenant;private final String mobile;private final MemberSmsScene scene;private final String code;private final Instant expires;
        private Delivery(Long tenant,String mobile,MemberSmsScene scene,String code,Instant expires) { this.tenant=tenant;this.mobile=mobile;this.scene=scene;this.code=code;this.expires=expires; }
    }
}
