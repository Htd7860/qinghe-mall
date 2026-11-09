package cn.qinghe.mall.module.system.service.tenant;
import cn.qinghe.mall.module.system.controller.app.storefront.vo.StorefrontRespVO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantDO;
import cn.qinghe.mall.module.system.dal.dataobject.tenant.TenantEntryDO;
import cn.qinghe.mall.module.system.dal.mysql.tenant.TenantEntryMapper;
import cn.qinghe.mall.module.system.framework.security.StorefrontProperties;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 择取yudao getTenantByWebsite的入口->租户->公开投影流程；B02改独立唯一映射/验证及受限历史语义。
 * @author 芋道源码
 */
@Service
public class StorefrontService {
    private final TenantEntryMapper entries;
    private final TenantService tenants;
    private final boolean allowLocal;
    private final Set<String> localHosts=new HashSet<>();
    public StorefrontService(TenantEntryMapper entries,TenantService tenants,StorefrontProperties properties) {
        this.entries=entries;this.tenants=tenants;this.allowLocal=properties.isAllowLocalCodeLookup();
        if (properties.getLocalCodeHosts()==null || properties.getLocalCodeHosts().size()>20) { throw new IllegalArgumentException("本地入口配置不正确"); }
        for (String host:properties.getLocalCodeHosts()) {
            String key=StorefrontHost.normalize(host);
            if (!StorefrontHost.isLocal(key)) { throw new IllegalArgumentException("编码定位只允许显式回环Host与端口"); }
            localHosts.add(key);
        }
    }
    public StorefrontRespVO resolve(String rawHost,String code) {
        final String host;
        try { host=StorefrontHost.normalize(rawHost); } catch (IllegalArgumentException ex) { throw exception0(400,"入口Host格式不正确"); }
        if (code!=null && !code.matches("[a-z0-9-]{3,32}")) { throw exception0(400,"店铺编码格式不正确"); }
        TenantEntryDO entry=entries.selectReservedByHost(host);TenantDO tenant;boolean local=false;
        if (entry!=null) {
            if (Boolean.TRUE.equals(entry.getDeleted()) || !Integer.valueOf(0).equals(entry.getStatus())
                    || !("STOREFRONT".equals(entry.getPurpose()) || "BOTH".equals(entry.getPurpose()))) { throw exception0(404,"商城入口不存在或已停用"); }
            if ("LOCAL".equals(entry.getKind())) {
                if (!allowLocal || !localHosts.contains(host) || !StorefrontHost.isLocal(host)) { throw exception0(404,"本地商城入口未启用"); }
            } else if (!"CUSTOM".equals(entry.getKind()) || entry.getDomainVerifiedAt()==null || entry.getHttpsVerifiedAt()==null
                    || !Objects.equals(entry.getTenantId(),entry.getVerifiedTenantId()) || !Objects.equals(entry.getBindingVersion(),entry.getVerifiedBindingVersion())) {
                throw exception0(404,"商城入口未完成验证");
            }
            tenant=tenants.validateForAuthentication(entry.getTenantId());
            if (code!=null && !code.equals(tenant.getCode())) { throw exception0(403,"入口与店铺编码不匹配"); }
        } else {
            if (!allowLocal || !StorefrontHost.isLocal(host) || !localHosts.contains(host) || code==null) { throw exception0(404,"商城入口不存在"); }
            tenant=tenants.getTenantByCode(code);
            if (tenant==null) { throw exception0(404,"商城不存在"); }
            tenant=tenants.validateForAuthentication(tenant.getId());local=true;
        }
        StorefrontRespVO result=new StorefrontRespVO();result.setTenantId(tenant.getId());result.setTenantCode(tenant.getCode());result.setName(tenant.getName());
        result.setCanPurchase(Integer.valueOf(0).equals(tenant.getStatus()) && tenant.getExpireTime().isAfter(LocalDateTime.now()));
        result.setTenantVersion(tenant.getAuthVersion());result.setLocalCodeLookup(local);
        if (entry!=null) { result.setEntryVersion(entry.getAuthVersion());result.setBindingVersion(entry.getBindingVersion()); }
        return result;
    }
}
