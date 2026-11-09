package cn.qinghe.mall.module.trade.framework.product;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原SPU/SKU API调用职责；固定来源、原Bearer、无重试/重定向/凭据日志，不在本地锁内RPC。 */
@Service
public class TradeProductClient {
    private final TradeProductApi api;private final SecurityProperties security;private final Semaphore calls=new Semaphore(16);
    public TradeProductClient(TradeProductApi api,SecurityProperties security,@Value("${qinghe.trade.product-base-url:http://127.0.0.1:8082}") String origin) {
        URI uri=URI.create(origin);
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && Arrays.asList("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost())))throw new IllegalArgumentException("商品来源必须是固定可信origin");
        this.api=api;this.security=security;
    }
    public Map<Long,ProductTradeSkuRespDTO> list(Collection<Long> ids) {
        if(ids==null || ids.stream().anyMatch(x->x==null || x<=0))throw exception0(400,"商品编号不正确");
        if(ids.isEmpty())return Collections.emptyMap();
        List<Long> wanted=new ArrayList<>(new TreeSet<>(ids));
        if(wanted.size()>200 || wanted.stream().anyMatch(x->x==null || x<=0))throw exception0(400,"商品查询数量不正确");
        List<ProductTradeSkuRespDTO> rows=call(()->api.list(bearer(),wanted));
        if(rows==null || rows.size()>wanted.size())throw exception0(503,"商品快照结果不完整");
        validate(rows);
        if(rows.stream().anyMatch(x->!wanted.contains(x.getSkuId())))throw exception0(503,"商品快照不匹配");
        return rows.stream().collect(Collectors.toMap(ProductTradeSkuRespDTO::getSkuId,x->x));
    }
    public PageResult<ProductTradeSkuRespDTO> page(String keyword,int pageNo,Long spuId) {
        if(keyword==null)keyword="";
        if(keyword.length()>64 || keyword.chars().anyMatch(Character::isISOControl) || spuId!=null && !id(spuId) || pageNo<1 || pageNo>10000)throw exception0(400,"商品查询条件不正确");
        final String text=keyword;PageResult<ProductTradeSkuRespDTO> result=call(()->api.page(bearer(),text,pageNo,20,spuId));
        if(result==null || result.getTotal()==null || result.getTotal()<0 || result.getList()==null || result.getList().size()>20)throw exception0(503,"商品列表响应不完整");
        validate(result.getList());return result;
    }
    private void validate(List<ProductTradeSkuRespDTO> rows) {
        Set<Long> seen=new HashSet<>();Long tenant=TenantContextHolder.getRequiredTenantId();
        for(ProductTradeSkuRespDTO row:rows) {
            if(row==null || !Objects.equals(tenant,row.getTenantId()) || !id(row.getSkuId()) || !seen.add(row.getSkuId()) || !id(row.getSpuId())
                    || !id(row.getCategoryId()) || !id(row.getDeliveryTemplateId()) || row.getSpuVersion()==null || row.getSpuVersion()<0 || row.getSkuVersion()==null || row.getSkuVersion()<0
                    || row.getName()==null || row.getPrice()==null || row.getPrice()<=0 || row.getPrice()>2_000_000_000 || row.getStock()==null || row.getStock()<0
                    || row.getProperties()==null)throw exception0(503,"商品销售快照不完整");
        }
    }
    private boolean id(Long x){return x!=null && x>0 && x<=9_007_199_254_740_991L;}
    private String bearer() {
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes))throw exception0(503,"商品读取缺少当前请求");
        String token=SecurityFrameworkUtils.obtainAuthorization(((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest(),security.getTokenHeader());
        if(token==null || token.length()>128)throw exception0(401,"请重新登录");return "Bearer "+token;
    }
    private <T> T call(Supplier<CommonResult<T>> call) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("商品RPC不能在购物车本地事务内执行");
        if(!calls.tryAcquire())throw exception0(503,"商品读取繁忙");
        try {CommonResult<T> result=call.get();if(result==null || result.getCode()==null || !result.isSuccess())throw exception0(503,"商品读取暂不可用");return result.getData();}
        finally {calls.release();}
    }
}
