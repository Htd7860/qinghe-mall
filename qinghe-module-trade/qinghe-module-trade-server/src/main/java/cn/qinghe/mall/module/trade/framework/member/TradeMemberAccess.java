package cn.qinghe.mall.module.trade.framework.member;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 身份由既有Token资格链验证真实会员；店铺复用system解析，RPC在交易库事务之前。 */
@Service @RequiredArgsConstructor
public class TradeMemberAccess {
    private final TradeStorefrontApi api;
    private final SecurityProperties security;
    private final Semaphore calls=new Semaphore(16);
    public LoginUser actor() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.MEMBER.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0
                || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"当前入口需要本店会员身份");
        if(actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L)throw exception0(503,"会员编号无法投影");
        return actor;
    }
    public MemberStorefrontRespDTO check(HttpServletRequest request,String store) {
        LoginUser actor=actor();
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("店铺校验不能置于交易库事务内");
        List<String> hosts=Collections.list(request.getHeaders("Host"));
        if(hosts.size()!=1 || hosts.get(0)==null || hosts.get(0).isEmpty() || hosts.get(0).length()>300
                || store!=null && !store.matches("[a-z0-9-]{3,32}"))throw exception0(400,"请提供唯一有效的商城入口");
        String token=SecurityFrameworkUtils.obtainAuthorization(request,security.getTokenHeader());
        if(token==null || token.length()>128)throw exception0(401,"请重新登录");
        if(!calls.tryAcquire())throw exception0(503,"商城入口服务繁忙");
        try {
            MemberStorefrontReqDTO body=new MemberStorefrontReqDTO();body.setRequestHost(hosts.get(0));body.setStore(store);
            CommonResult<MemberStorefrontRespDTO> result=api.current("Bearer "+token,body);
            if(result==null || result.getCode()==null)throw exception0(503,"商城入口结果不完整");
            result.checkError();MemberStorefrontRespDTO entry=result.getData();
            if(entry==null || !Objects.equals(actor.getTenantId(),entry.getTenantId()) || entry.getCanPurchase()==null
                    || entry.getTenantCode()==null || !entry.getTenantCode().matches("[a-z0-9-]{3,32}")
                    || store!=null && !store.equals(entry.getTenantCode()))throw exception0(503,"商城入口结果不完整");
            if(entry.getTenantVersion()==null || entry.getTenantVersion()<0 || entry.getLocalCodeLookup()==null
                    || Boolean.TRUE.equals(entry.getLocalCodeLookup()) && (entry.getEntryVersion()!=null || entry.getBindingVersion()!=null)
                    || !Boolean.TRUE.equals(entry.getLocalCodeLookup()) && (entry.getEntryVersion()==null || entry.getEntryVersion()<0 || entry.getBindingVersion()==null || entry.getBindingVersion()<0))throw exception0(503,"商城入口版本不完整");
            actor();return entry;
        } finally {calls.release();}
    }
}
