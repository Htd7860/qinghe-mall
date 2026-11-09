package cn.qinghe.mall.module.product.service.spu;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.product.controller.app.spu.vo.*;
import cn.qinghe.mall.module.product.dal.mysql.spu.AppProductSpuMapper;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import cn.qinghe.mall.module.product.framework.storefront.ProductStorefrontAccess;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 直接择取AppProductSpuController page/detail的上架检查、SKU读取和销售投影，不经过后台owner权限。
 * @author 芋道源码
 */
@Service
public class AppProductSpuService {
    private final ProductStorefrontAccess access;
    private final AppProductSpuMapper mapper;
    private final Validator validator;
    private final int maxSkus;
    private final TransactionTemplate read;
    public AppProductSpuService(ProductStorefrontAccess access,AppProductSpuMapper mapper,Validator validator,
            ProductSkuProperties settings,PlatformTransactionManager manager) {
        this.access=access;this.mapper=mapper;this.validator=validator;maxSkus=settings.getMaxSkus();
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public PageResult<AppProductSpuRespVO> page(HttpServletRequest request,String store,AppProductSpuPageReqVO body) {
        outside();AppProductSpuPageReqVO q=query(body);
        MemberStorefrontRespDTO entry=access.resolve(request,store);Long tenant=entry.getTenantId();
        return TenantUtils.execute(tenant,()->read.execute(tx->{
            Long total=mapper.count(tenant,q);if(total==null || total<0 || total>Integer.MAX_VALUE)throw incomplete();
            int offset=(q.getPageNo()-1)*q.getPageSize();if(offset>=total)return PageResult.empty(total);
            List<AppProductSpuMapper.Row> rows=mapper.page(tenant,q,offset,q.getPageSize());
            if(rows==null || rows.isEmpty() || rows.size()>q.getPageSize())throw incomplete();
            List<AppProductSpuRespVO> result=new ArrayList<>();Set<Long> seen=new HashSet<>();
            for(AppProductSpuMapper.Row row:rows) {verify(row,tenant);if(!seen.add(row.getId()))throw incomplete();result.add(view(row,entry));}
            return new PageResult<>(total,result);
        }));
    }
    public AppProductSpuDetailRespVO detail(HttpServletRequest request,String store,Long id) {
        outside();if(!id(id))throw exception0(400,"商品编号不正确");
        MemberStorefrontRespDTO entry=access.resolve(request,store);Long tenant=entry.getTenantId();
        return TenantUtils.execute(tenant,()->read.execute(tx->{
            AppProductSpuMapper.Row row=mapper.detail(tenant,id);if(row==null)throw exception0(404,"商品不存在或已下架");
            verify(row,tenant);if(!id.equals(row.getId()) || row.getDescription()==null || row.getDescription().length()>200000)throw incomplete();
            AppProductSpuDetailRespVO result=BeanUtils.toBean(view(row,entry),AppProductSpuDetailRespVO.class);
            result.setDescription(row.getDescription());
            String[] sliders=JsonUtils.parseObject(row.getSliderJson(),String[].class);
            if(sliders==null || sliders.length<1 || sliders.length>20 || Arrays.stream(sliders).anyMatch(x->!text(x,1024)))throw incomplete();
            result.setSliderPicUrls(Arrays.asList(sliders));result.setDisplayProperties(properties(row.getDisplayJson(),100,false));
            List<AppProductSpuMapper.SkuRow> rows=mapper.skus(tenant,id,maxSkus+1);
            if(rows==null || rows.isEmpty() || rows.size()>maxSkus || !Boolean.TRUE.equals(row.getSpecType()) && rows.size()!=1)throw incomplete();
            List<AppProductSpuDetailRespVO.Sku> skus=new ArrayList<>();Set<Long> seen=new HashSet<>();long stock=0;int price=Integer.MAX_VALUE,market=Integer.MAX_VALUE;
            for(AppProductSpuMapper.SkuRow sku:rows) {
                if(sku==null || !Objects.equals(tenant,sku.getTenantId()) || !id.equals(sku.getSpuId()) || !id(sku.getId()) || !seen.add(sku.getId())
                        || !price(sku.getPrice(),1) || !price(sku.getMarketPrice(),0) || sku.getStock()==null || sku.getStock()<0 || !text(sku.getPicUrl(),1024))throw incomplete();
                AppProductSpuDetailRespVO.Sku item=BeanUtils.toBean(sku,AppProductSpuDetailRespVO.Sku.class);
                item.setProperties(properties(sku.getPropertiesJson(),20,!Boolean.TRUE.equals(row.getSpecType())));
                if(Boolean.TRUE.equals(row.getSpecType()) && item.getProperties().isEmpty())throw incomplete();
                skus.add(item);
                stock+=sku.getStock();price=Math.min(price,sku.getPrice());market=Math.min(market,sku.getMarketPrice());
            }
            if(stock!=row.getStock().longValue() || price!=row.getPrice() || market!=row.getMarketPrice())throw incomplete();
            result.setSkus(skus);return result;
        }));
    }
    public AppProductCatalogRespVO options(HttpServletRequest request,String store) {
        outside();MemberStorefrontRespDTO entry=access.resolve(request,store);Long tenant=entry.getTenantId();
        return TenantUtils.execute(tenant,()->read.execute(tx->{
            List<AppProductCatalogRespVO.Category> categories=mapper.categories(tenant);List<AppProductCatalogRespVO.Brand> brands=mapper.brands(tenant);
            if(categories==null || categories.size()>2000 || brands==null || brands.size()>500)throw exception0(503,"商品筛选项超出当前展示范围");
            Set<Long> ids=new HashSet<>();Map<Long,Long> parents=new HashMap<>();
            for(AppProductCatalogRespVO.Category c:categories) {
                if(c==null || !id(c.getId()) || !ids.add(c.getId()) || c.getParentId()==null || c.getParentId()<0 || c.getParentId()>9007199254740991L
                        || !text(c.getName(),100) || !text(c.getPicUrl(),1024))throw incomplete();parents.put(c.getId(),c.getParentId());
            }
            for(Long id:ids) {
                Set<Long> path=new HashSet<>();Long next=id;
                while(!Long.valueOf(0).equals(next)) {if(next==null || !path.add(next) || path.size()>3)throw incomplete();next=parents.get(next);}
            }
            ids.clear();for(AppProductCatalogRespVO.Brand b:brands)if(b==null || !id(b.getId()) || !ids.add(b.getId()) || !text(b.getName(),100) || b.getPicUrl()==null || b.getPicUrl().length()>1024)throw incomplete();
            AppProductCatalogRespVO out=new AppProductCatalogRespVO();out.setCategories(categories);out.setBrands(brands);return out;
        }));
    }
    private AppProductSpuPageReqVO query(AppProductSpuPageReqVO body) {
        if(body==null || !validator.validate(body).isEmpty() || body.getPageNo()==null || body.getPageNo()<1 || body.getPageNo()>10000
                || body.getPageSize()==null || body.getPageSize()<1 || body.getPageSize()>20
                || body.getKeyword()!=null && body.getKeyword().chars().anyMatch(Character::isISOControl)
                || body.getMinPrice()!=null && body.getMaxPrice()!=null && body.getMinPrice()>body.getMaxPrice())throw exception0(400,"商品筛选条件不正确");
        AppProductSpuPageReqVO q=BeanUtils.toBean(body,AppProductSpuPageReqVO.class);
        String keyword=q.getKeyword()==null?"":q.getKeyword().trim();
        q.setKeyword(keyword.isEmpty()?null:"%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        q.setSortAsc(!Boolean.FALSE.equals(q.getSortAsc()));return q;
    }
    private AppProductSpuRespVO view(AppProductSpuMapper.Row row,MemberStorefrontRespDTO entry) {
        AppProductSpuRespVO out=BeanUtils.toBean(row,AppProductSpuRespVO.class);out.setCanPurchase(Boolean.TRUE.equals(entry.getCanPurchase()) && row.getStock()>0);return out;
    }
    /** 搜索协调器的同一只读视图内调用；沿用原销售投影核对，不返回ES销售字段。 */
    public List<AppProductSpuRespVO> searchRows(MemberStorefrontRespDTO entry,List<Long> ids) {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || entry==null || !Objects.equals(entry.getTenantId(),cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.getTenantId())
                || cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder.isIgnore() || ids==null || ids.size()>20
                || ids.stream().anyMatch(x->!id(x)) || new HashSet<>(ids).size()!=ids.size())throw incomplete();
        if(ids.isEmpty())return Collections.emptyList();
        List<AppProductSpuMapper.Row> rows=mapper.searchRows(entry.getTenantId(),ids);
        if(rows==null || rows.size()!=ids.size())throw exception0(503,"商品搜索资料更新中，请稍后重试");
        Map<Long,AppProductSpuRespVO> byId=new HashMap<>();
        for(AppProductSpuMapper.Row row:rows) {
            verify(row,entry.getTenantId());
            if(!ids.contains(row.getId()) || byId.put(row.getId(),view(row,entry))!=null)throw incomplete();
        }
        List<AppProductSpuRespVO> result=new ArrayList<>();for(Long id:ids)result.add(byId.get(id));return result;
    }
    private void verify(AppProductSpuMapper.Row row,Long tenant) {
        if(row==null || !Objects.equals(tenant,row.getTenantId()) || !id(row.getId()) || !id(row.getCategoryId()) || !id(row.getBrandId())
                || !text(row.getName(),128) || row.getIntroduction()==null || row.getIntroduction().length()>512
                || !text(row.getCategoryName(),100) || !text(row.getBrandName(),100) || !text(row.getPicUrl(),1024) || row.getSpecType()==null
                || !price(row.getPrice(),1) || !price(row.getMarketPrice(),0) || row.getStock()==null || row.getStock()<0 || row.getSalesCount()==null || row.getSalesCount()<0)throw incomplete();
    }
    private List<ProductTradeSkuRespDTO.Property> properties(String json,int max,boolean allowDefault) {
        if(json==null)return Collections.emptyList();
        ProductTradeSkuRespDTO.Property[] rows=JsonUtils.parseObject(json,ProductTradeSkuRespDTO.Property[].class);
        if(rows==null || rows.length>max)throw incomplete();
        for(ProductTradeSkuRespDTO.Property p:rows) {
            boolean defaultValue=p!=null && allowDefault && rows.length==1 && Long.valueOf(0).equals(p.getPropertyId()) && Long.valueOf(0).equals(p.getValueId());
            if(p==null || !defaultValue && (!id(p.getPropertyId()) || !id(p.getValueId())) || !text(p.getPropertyName(),100) || !text(p.getValueName(),100))throw incomplete();
        }
        return Arrays.asList(rows);
    }
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("商品浏览不能嵌套外层事务");}
    private boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
    private boolean price(Integer n,int min){return n!=null && n>=min && n<=2000000000;}
    private boolean text(String s,int max){return s!=null && !s.trim().isEmpty() && s.length()<=max;}
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete(){return exception0(503,"商品销售资料不完整，请稍后重试");}
}
