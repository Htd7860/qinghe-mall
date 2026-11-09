package cn.qinghe.mall.module.member.framework.auth;

import cn.qinghe.mall.framework.redis.core.ratelimit.LoginFailureLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.function.Supplier;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 直接复用原后台Redis/Lua与有界本地影子，不维护第二份限额算法。 */
@Service
public class MemberLoginFailureLimiter {
    private final LoginFailureLimiter limiter;
    public MemberLoginFailureLimiter(StringRedisTemplate redis,@Value("${qinghe.redis.key-prefix}") String prefix,
            MemberLoginFailureProperties settings) {
        limiter=new LoginFailureLimiter(redis,prefix,settings.getMaxFailures(),settings.getWindowSeconds(),settings.getMaxLocalSubjects());
    }
    public <T> T execute(Long tenantId,String mobile,Supplier<T> action) {
        if (tenantId==null || tenantId<=0 || mobile==null || !mobile.matches("^1[3-9][0-9]{9}$")) {
            throw exception0(400,"登录限额主体不正确");
        }
        // 固定消费者client+真实tenantId+规范手机号，不因Host/编码别名轮换而清空失败量。
        return limiter.execute("qinghe-app",tenantId.toString(),mobile,action);
    }
}
