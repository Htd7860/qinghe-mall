package cn.qinghe.mall.module.promotion.framework.product;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.config.SecurityProperties;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 沿用现有商品范围 RPC 的固定来源/原 Bearer/有界并发/无重试，外部调用不持活动库事务。 */
@Service
public class SeckillProductClient {
    private final SeckillProductApi api;
    private final SecurityProperties security;
    private final Semaphore calls=new Semaphore(16);

    public SeckillProductClient(SeckillProductApi api,SecurityProperties security,
            @Value("${qinghe.promotion.product-base-url:http://127.0.0.1:8082}") String source) {
        URI uri=URI.create(source);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())))
            throw new IllegalArgumentException("秒杀商品来源必须为固定可信origin");
        this.api=api;this.security=security;
    }

    public SeckillProductSnapshotRespDTO snapshot(Long spuId,List<Long> ids) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("活动商品RPC不能置于本地事务内");
        RequestAttributes attrs=RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes)) throw exception0(503,"活动商品校验缺少当前请求");
        String token=SecurityFrameworkUtils.obtainAuthorization(((ServletRequestAttributes)attrs).getRequest(),security.getTokenHeader());
        if (token==null || token.length()>128) throw exception0(401,"缺少当前访问凭据");
        if (!calls.tryAcquire()) throw exception0(503,"活动商品校验繁忙");
        try {
            SeckillProductSnapshotReqDTO body=new SeckillProductSnapshotReqDTO();body.setSpuId(spuId);body.setSkuIds(new ArrayList<>(ids));
            CommonResult<SeckillProductSnapshotRespDTO> answer;
            try { answer=api.snapshot("Bearer "+token,body); }
            catch (RuntimeException unavailable) { throw exception0(503,"活动商品服务暂不可用"); }
            if (answer!=null && (Integer.valueOf(400).equals(answer.getCode()) || Integer.valueOf(404).equals(answer.getCode())))
                throw exception0(400,"活动商品或规格不存在、未上架或无权使用");
            if (answer==null || !answer.isSuccess()) throw exception0(503,"活动商品校验未确认");
            SeckillProductSnapshotRespDTO result=answer.getData();
            if (result==null || !Objects.equals(TenantContextHolder.getTenantId(),result.getTenantId())
                    || !Objects.equals(spuId,result.getSpuId()) || !version(result.getSpuVersion())
                    || !text(result.getProductName(),128) || !text(result.getPicUrl(),1024)
                    || result.getSkus()==null || result.getSkus().size()!=ids.size()) throw incomplete();
            Set<Long> seen=new HashSet<>();
            for (SeckillProductSnapshotRespDTO.Sku sku:result.getSkus()) {
                if (sku==null || !ids.contains(sku.getSkuId()) || !seen.add(sku.getSkuId()) || !version(sku.getSkuVersion())
                        || sku.getPrice()==null || sku.getPrice()<1 || sku.getPrice()>2_000_000_000
                        || sku.getProperties()==null || sku.getProperties().isEmpty() || sku.getProperties().size()>20) throw incomplete();
                Set<Long> dimensions=new HashSet<>();
                for (ProductTradeSkuRespDTO.Property property:sku.getProperties()) {
                    if (property==null || property.getPropertyId()==null || property.getPropertyId()<0 || property.getPropertyId()>9_007_199_254_740_991L
                            || property.getValueId()==null || property.getValueId()<0 || property.getValueId()>9_007_199_254_740_991L
                            || !dimensions.add(property.getPropertyId()) || !text(property.getPropertyName(),100) || !text(property.getValueName(),100)) throw incomplete();
                }
            }
            result.setSkus(result.getSkus().stream().sorted(Comparator.comparing(SeckillProductSnapshotRespDTO.Sku::getSkuId)).collect(Collectors.toList()));
            return result;
        } finally { calls.release(); }
    }
    private boolean version(Long value) { return value!=null && value>=0 && value<=9_007_199_254_740_991L; }
    private boolean text(String value,int max) { return value!=null && !value.trim().isEmpty() && value.length()<=max && value.chars().noneMatch(Character::isISOControl); }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"活动商品快照不完整"); }
}
