package cn.qinghe.mall.module.member.framework.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.api.tenant.MemberStorefrontApi;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 直接复用system当前StorefrontService；不通过前端tenantId、Origin或转发头定位店铺。 */
@Service
public class MemberStorefrontClient {
    private final MemberStorefrontApi api;
    private final String serviceToken;
    private final Semaphore calls;
    public MemberStorefrontClient(MemberStorefrontApi api,MemberTokenClientProperties settings) {
        this.api=api;this.serviceToken=settings.validatedServiceToken();
        this.calls=new Semaphore(settings.getMaxConcurrentCalls());
    }

    public MemberStorefrontRespDTO resolveRequest(HttpServletRequest request,String store) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("入口解析RPC不能在会员数据库事务内执行");
        }
        if (request==null || TenantContextHolder.isIgnore()) { throw exception0(403,"商城入口上下文不正确"); }
        List<String> hosts=Collections.list(request.getHeaders("Host"));
        if (hosts.size()!=1 || hosts.get(0)==null || hosts.get(0).isEmpty() || hosts.get(0).length()>300
                || store!=null && !store.matches("[a-z0-9-]{3,32}")) {
            throw exception0(400,"请提供唯一、有效的商城入口");
        }
        if (serviceToken.isEmpty()) { throw exception0(503,"会员内部认证凭据未配置"); }
        if (!calls.tryAcquire()) { throw exception0(503,"商城入口解析繁忙，请稍后重试"); }
        try {
            MemberStorefrontReqDTO body=new MemberStorefrontReqDTO();body.setRequestHost(hosts.get(0));body.setStore(store);
            CommonResult<MemberStorefrontRespDTO> response;
            try { response=api.resolve("Bearer "+serviceToken,body); }
            catch (RuntimeException unavailable) { throw exception0(503,"商城入口服务暂不可用"); }
            if (response==null || response.getCode()==null) { throw exception0(503,"商城入口结果不完整"); }
            if (Integer.valueOf(400).equals(response.getCode())) { throw exception0(400,"商城入口格式不正确"); }
            if (Integer.valueOf(403).equals(response.getCode())) { throw exception0(403,"商城入口与店铺不匹配"); }
            if (Integer.valueOf(401).equals(response.getCode()) || Integer.valueOf(404).equals(response.getCode())) {
                throw exception0(404,"商城入口不存在或已停用");
            }
            if (!response.isSuccess()) { throw exception0(503,"商城入口服务暂不可用"); }
            MemberStorefrontRespDTO entry=response.getData();
            if (entry==null || entry.getTenantId()==null || entry.getTenantId()<=0 || entry.getTenantCode()==null
                    || !entry.getTenantCode().matches("[a-z0-9-]{3,32}") || entry.getTenantVersion()==null || entry.getTenantVersion()<0
                    || entry.getCanPurchase()==null || entry.getLocalCodeLookup()==null
                    || store!=null && !store.equals(entry.getTenantCode())) {
                throw exception0(503,"商城入口归属信息不完整");
            }
            if (Boolean.TRUE.equals(entry.getLocalCodeLookup())) {
                if (entry.getEntryVersion()!=null || entry.getBindingVersion()!=null) { throw exception0(503,"本地入口版本不正确"); }
            } else if (entry.getEntryVersion()==null || entry.getEntryVersion()<0
                    || entry.getBindingVersion()==null || entry.getBindingVersion()<0) {
                throw exception0(503,"商城入口版本不完整");
            }
            Long claimed=TenantContextHolder.getTenantId();
            if (claimed!=null && !claimed.equals(entry.getTenantId())) { throw exception0(403,"商城入口与请求租户不匹配"); }
            return entry;
        } finally { calls.release(); }
    }

    /** 不能用浏览器上一次resolve结果代替服务端重读；不缓存、不按新tenant静默切换。 */
    public MemberStorefrontRespDTO requireSameRequest(HttpServletRequest request,String store,MemberStorefrontRespDTO expected) {
        Objects.requireNonNull(expected,"原入口结果不能为空");
        MemberStorefrontRespDTO current=resolveRequest(request,store);
        if (!Objects.equals(expected.getTenantId(),current.getTenantId())
                || !Objects.equals(expected.getTenantCode(),current.getTenantCode())
                || !Objects.equals(expected.getTenantVersion(),current.getTenantVersion())
                || !Objects.equals(expected.getEntryVersion(),current.getEntryVersion())
                || !Objects.equals(expected.getBindingVersion(),current.getBindingVersion())
                || !Objects.equals(expected.getLocalCodeLookup(),current.getLocalCodeLookup())) {
            throw exception0(409,"商城入口已变化，请重新打开店铺后登录");
        }
        return current;
    }
}
