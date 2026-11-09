package cn.qinghe.mall.module.promotion.service.seckill;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.promotion.controller.app.seckill.vo.AppSeckillActivityRespVO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillActivitySkuDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.framework.member.PromotionStorefrontAccess;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 复用App活动分页/详情和真实店铺解析；公开销售浏览不等于会员认证。 @author 芋道源码 */
@Service
public class AppSeckillActivityService {
    private final PromotionStorefrontAccess access;
    private final AppSeckillActivityMapper mapper;
    private final SeckillActivityMapper activities;
    private final boolean enabled;
    private final TransactionTemplate read;
    public AppSeckillActivityService(PromotionStorefrontAccess access,AppSeckillActivityMapper mapper,SeckillActivityMapper activities,
            PlatformTransactionManager manager,@Value("${qinghe.promotion.seckill-results.enabled:false}") boolean enabled) {
        this.access=access;this.mapper=mapper;this.activities=activities;this.enabled=enabled;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public PageResult<AppSeckillActivityRespVO> page(HttpServletRequest request,String store,PageParam query) {
        outside();if(query==null || query.getPageNo()==null || query.getPageNo()<1 || query.getPageNo()>10000
                || query.getPageSize()==null || query.getPageSize()<1 || query.getPageSize()>20)throw exception0(400,"每页最多20条活动");
        MemberStorefrontRespDTO entry=access.resolve(request,store);Long tenant=entry.getTenantId();
        return TenantUtils.execute(tenant,()->read.execute(tx->{
            current(tenant);Long total=mapper.count(tenant);if(total==null || total<0 || total>Integer.MAX_VALUE)throw incomplete();
            int offset=(query.getPageNo()-1)*query.getPageSize();if(offset>=total)return PageResult.empty(total);
            LocalDateTime now=mapper.now();List<AppSeckillActivityMapper.Row> rows=mapper.page(tenant,offset,query.getPageSize());
            if(rows==null || rows.isEmpty() || rows.size()>query.getPageSize())throw incomplete();
            List<AppSeckillActivityRespVO> result=new ArrayList<>();
            for(AppSeckillActivityMapper.Row row:rows)result.add(view(row,tenant,entry,now));return new PageResult<>(total,result);
        }));
    }
    public AppSeckillActivityRespVO get(HttpServletRequest request,String store,Long id) {
        outside();if(!id(id))throw exception0(400,"活动编号不正确");
        MemberStorefrontRespDTO entry=access.resolve(request,store);Long tenant=entry.getTenantId();
        return TenantUtils.execute(tenant,()->read.execute(tx->{
            current(tenant);AppSeckillActivityMapper.Row row=mapper.get(tenant,id);if(row==null)throw exception0(404,"本店活动不存在或尚未发布");
            AppSeckillActivityRespVO out=view(row,tenant,entry,mapper.now());List<SeckillActivitySkuDO> pools=activities.skus(tenant,id);
            if(pools==null || pools.isEmpty() || pools.size()>200)throw incomplete();
            List<AppSeckillActivityRespVO.Sku> skus=new ArrayList<>();Set<Long> seen=new HashSet<>();
            for(SeckillActivitySkuDO p:pools) {
                if(p==null || !Objects.equals(p.getTenantId(),tenant) || !Objects.equals(p.getActivityId(),id) || !id(p.getSkuId())
                        || !seen.add(p.getSkuId()) || !Integer.valueOf(20).equals(p.getAllocationStatus()) || p.getAvailableQuantity()==null || p.getAvailableQuantity()<0
                        || p.getPriceCent()==null || p.getPriceCent()<1 || p.getPriceCent()>2000000000)throw incomplete();
                ProductTradeSkuRespDTO.Property[] properties=JsonUtils.parseObject(p.getSpecSnapshot(),ProductTradeSkuRespDTO.Property[].class);
                if(properties==null || properties.length>100)throw incomplete();
                for(ProductTradeSkuRespDTO.Property property:properties)if(property==null || !id(property.getPropertyId()) || !id(property.getValueId())
                        || property.getPropertyName()==null || property.getPropertyName().isEmpty() || property.getValueName()==null || property.getValueName().isEmpty())throw incomplete();
                AppSeckillActivityRespVO.Sku sku=new AppSeckillActivityRespVO.Sku();sku.setSkuId(p.getSkuId());sku.setProperties(Arrays.asList(properties));
                sku.setPriceCent(p.getPriceCent());sku.setAvailableQuantity(p.getAvailableQuantity());skus.add(sku);
            }
            out.setSkus(skus);return out;
        }));
    }
    private AppSeckillActivityRespVO view(AppSeckillActivityMapper.Row row,Long tenant,MemberStorefrontRespDTO entry,LocalDateTime now) {
        if(row==null || !id(row.getId()) || !Objects.equals(row.getTenantId(),tenant) || !id(row.getProductSpuId())
                || row.getName()==null || row.getProductName()==null || row.getPicUrl()==null || row.getGeneration()==null || row.getGeneration()<1
                || !Arrays.asList(20,30,40,50).contains(row.getStatus()) || row.getMinPriceCent()==null || row.getMinPriceCent()<1 || row.getMinPriceCent()>2000000000
                || row.getSingleLimit()==null || row.getSingleLimit()<1 || row.getMemberLimit()==null || row.getMemberLimit()<row.getSingleLimit()
                || now==null || row.getStartTime()==null || row.getEndTime()==null || !row.getStartTime().isBefore(row.getEndTime()))throw incomplete();
        AppSeckillActivityRespVO out=new AppSeckillActivityRespVO();out.setId(row.getId());out.setName(row.getName());out.setSpuId(row.getProductSpuId());
        out.setProductName(row.getProductName());out.setPicUrl(row.getPicUrl());out.setGeneration(row.getGeneration());out.setStartTime(row.getStartTime().atOffset(ZoneOffset.UTC));
        out.setEndTime(row.getEndTime().atOffset(ZoneOffset.UTC));out.setSingleLimit(row.getSingleLimit());out.setMemberLimit(row.getMemberLimit());
        out.setStatus(row.getStatus());out.setMinPriceCent(row.getMinPriceCent());out.setServerTime(now.toInstant(ZoneOffset.UTC).toEpochMilli());
        out.setQuoteAvailable(enabled && Boolean.TRUE.equals(entry.getCanPurchase()) && Integer.valueOf(20).equals(row.getStatus())
                && Integer.valueOf(20).equals(row.getRuntimeStage()) && !now.isBefore(row.getStartTime()) && now.isBefore(row.getEndTime()));return out;
    }
    private void current(Long tenant){if(TenantContextHolder.isIgnore() || !Objects.equals(tenant,TenantContextHolder.getTenantId()))throw exception0(403,"活动查询店铺上下文不正确");}
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("活动浏览不能嵌套外事务");}
    private boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete(){return exception0(503,"活动展示资料不完整，请稍后重试");}
}
