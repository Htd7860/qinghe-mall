package cn.qinghe.mall.module.trade.service.cart;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.trade.controller.app.cart.vo.*;
import cn.qinghe.mall.module.trade.dal.dataobject.cart.CartDO;
import cn.qinghe.mall.module.trade.dal.mysql.cart.CartMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.framework.product.TradeProductClient;
import cn.qinghe.mall.module.trade.framework.cart.CartProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 直接择取原CartServiceImpl的SKU合并/改量/勾选/换规格/删除/真实SPU-SKU拼接。
 * 当前项目适配：实际Host/会员、购物车父版本和资源上限、RPC在本地事务外、失效项不自动删除。
 * @author 芋道源码
 */
@Service
public class CartServiceImpl {
    private final CartMapper mapper;private final TradeMemberAccess access;private final TradeProductClient products;private final CartProperties limits;
    private final TransactionTemplate write,read;
    public CartServiceImpl(CartMapper mapper,TradeMemberAccess access,TradeProductClient products,CartProperties limits,PlatformTransactionManager manager) {
        this.mapper=mapper;this.access=access;this.products=products;this.limits=limits;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public Long add(HttpServletRequest request,String store,AppCartAddReqVO body) {
        purchase(access.check(request,store));LoginUser user=access.actor();quantity(body.getCount());id(body.getSkuId());
        ProductTradeSkuRespDTO product=product(body.getSkuId());
        return change(user,body.getExpectedVersion(),()->{
            CartDO old=mapper.findSku(user.getTenantId(),user.getId(),body.getSkuId());
            int count=Math.addExact(old==null?0:old.getCount(),body.getCount());available(product,count);
            if(old!=null){sameSpu(old,product);changed(mapper.change(user.getTenantId(),user.getId(),old.getId(),count,true));return old.getId();}
            if(rows(user).size()>=limits.getMaxCartLines())throw exception0(409,"购物车商品种类已达到上限");
            return insert(user,product,count);
        });
    }
    public void count(HttpServletRequest request,String store,AppCartUpdateCountReqVO body) {
        purchase(access.check(request,store));LoginUser user=access.actor();quantity(body.getCount());
        CartDO original=required(user,body.getId());ProductTradeSkuRespDTO product=product(original.getSkuId());
        change(user,body.getExpectedVersion(),()->{CartDO row=required(user,body.getId());available(product,body.getCount());
            sameSpu(row,product);changed(mapper.change(user.getTenantId(),user.getId(),row.getId(),body.getCount(),row.getSelected()));return true;});
    }
    public void selected(HttpServletRequest request,String store,AppCartUpdateSelectedReqVO body) {
        MemberStorefrontRespDTO entry=access.check(request,store);LoginUser user=access.actor();ids(body.getIds());
        if(Boolean.TRUE.equals(body.getSelected()))purchase(entry);
        List<CartDO> original=owned(user,body.getIds());
        Map<Long,ProductTradeSkuRespDTO> snapshots=Boolean.TRUE.equals(body.getSelected())?products.list(original.stream().map(CartDO::getSkuId).collect(Collectors.toList())):Collections.emptyMap();
        change(user,body.getExpectedVersion(),()->{for(CartDO row:owned(user,body.getIds())) {
            if(Boolean.TRUE.equals(body.getSelected())){ProductTradeSkuRespDTO product=snapshots.get(row.getSkuId());available(product,row.getCount());sameSpu(row,product);}
            changed(mapper.change(user.getTenantId(),user.getId(),row.getId(),row.getCount(),body.getSelected()));
        }return true;});
    }
    public Long reset(HttpServletRequest request,String store,AppCartResetReqVO body) {
        purchase(access.check(request,store));LoginUser user=access.actor();quantity(body.getCount());id(body.getSkuId());
        required(user,body.getId());ProductTradeSkuRespDTO product=product(body.getSkuId());
        return change(user,body.getExpectedVersion(),()->{
            CartDO old=required(user,body.getId());sameSpu(old,product);
            CartDO target=mapper.findSku(user.getTenantId(),user.getId(),body.getSkuId());
            int count=body.getCount();if(target!=null && !target.getId().equals(old.getId()))count=Math.addExact(target.getCount(),count);
            available(product,count);
            if(target!=null && target.getId().equals(old.getId())){changed(mapper.change(user.getTenantId(),user.getId(),old.getId(),count,true));return old.getId();}
            changed(mapper.retire(user.getTenantId(),user.getId(),old.getId()));
            if(target!=null){changed(mapper.change(user.getTenantId(),user.getId(),target.getId(),count,true));return target.getId();}
            return insert(user,product,count);
        });
    }
    public void delete(HttpServletRequest request,String store,AppCartDeleteReqVO body) {
        access.check(request,store);LoginUser user=access.actor();ids(body.getIds());
        change(user,body.getExpectedVersion(),()->{for(CartDO row:owned(user,body.getIds()))changed(mapper.retire(user.getTenantId(),user.getId(),row.getId()));return true;});
    }
    public AppCartListRespVO list(HttpServletRequest request,String store) {
        MemberStorefrontRespDTO entry=access.check(request,store);LoginUser user=access.actor();
        Snapshot snapshot=read.execute(status->{Snapshot value=new Snapshot();Long version=mapper.readVersion(user.getTenantId(),user.getId());value.version=version==null?0:version;value.rows=rows(user);return value;});
        if(snapshot==null)throw exception0(503,"购物车读取未完成");
        Map<Long,ProductTradeSkuRespDTO> current=products.list(snapshot.rows.stream().map(CartDO::getSkuId).collect(Collectors.toList()));
        List<AppCartListRespVO.Line> lines=new ArrayList<>();
        for(CartDO row:snapshot.rows) {
            id(row.getId());ProductTradeSkuRespDTO product=current.get(row.getSkuId());
            AppCartListRespVO.Line line=BeanUtils.toBean(row,AppCartListRespVO.Line.class);
            if(product!=null && !Objects.equals(row.getSpuId(),product.getSpuId()))throw exception0(503,"商品规格归属异常");
            String reason=product==null?"商品或规格已失效":row.getCount()>limits.getMaxItemQuantity()?"数量超过当前限制":product.getStock()<row.getCount()?"库存不足":"";
            line.setProduct(product);line.setValid(reason.isEmpty());line.setInvalidReason(reason);lines.add(line);
        }
        access.actor();AppCartListRespVO result=new AppCartListRespVO();result.setVersion(snapshot.version);result.setCanPurchase(entry.getCanPurchase());
        result.setMaxCartLines(limits.getMaxCartLines());result.setMaxItemQuantity(limits.getMaxItemQuantity());result.setMaxOrderLines(limits.getMaxOrderLines());result.setLines(lines);return result;
    }
    public Integer countAll(HttpServletRequest request,String store) {
        access.check(request,store);LoginUser user=access.actor();int total=0;for(CartDO row:rows(user))total=Math.addExact(total,row.getCount());return total;
    }
    public PageResult<ProductTradeSkuRespDTO> options(HttpServletRequest request,String store,String keyword,int pageNo,Long spuId) {
        access.check(request,store);PageResult<ProductTradeSkuRespDTO> result=products.page(keyword,pageNo,spuId);access.actor();return result;
    }
    private <T>T change(LoginUser user,Long expected,Supplier<T> action) {
        if(expected==null || expected<0 || expected>=9_007_199_254_740_991L)throw exception0(400,"购物车版本不正确");
        return write.execute(status->{mapper.ensureUser(user.getTenantId(),user.getId());Long version=mapper.lockVersion(user.getTenantId(),user.getId());
            if(!Objects.equals(version,expected))throw exception0(409,"购物车已变化，请重新读取后再操作");
            LoginUser current=access.actor();if(!Objects.equals(current.getId(),user.getId()) || !Objects.equals(current.getTenantId(),user.getTenantId()))throw exception0(403,"购物车身份已变化");
            T result=action.get();changed(mapper.bump(user.getTenantId(),user.getId(),expected));return result;});
    }
    private Long insert(LoginUser user,ProductTradeSkuRespDTO product,int count) {
        CartDO row=new CartDO();row.setTenantId(user.getTenantId());row.setUserId(user.getId());row.setSpuId(product.getSpuId());row.setSkuId(product.getSkuId());
        row.setCount(count);row.setSelected(true);row.setAuthVersion(0L);row.setDeleted(false);row.setCreator(user.getId().toString());row.setUpdater(user.getId().toString());
        row.setCreateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));row.setUpdateTime(row.getCreateTime());
        changed(mapper.insert(row));id(row.getId());return row.getId();
    }
    private CartDO required(LoginUser user,Long id){id(id);CartDO row=mapper.find(user.getTenantId(),user.getId(),id);if(row==null)throw exception0(404,"购物车商品不存在");return row;}
    private List<CartDO> rows(LoginUser user){List<CartDO> rows=mapper.list(user.getTenantId(),user.getId());if(rows.size()>200)throw exception0(503,"购物车记录超出支持范围");return rows;}
    private List<CartDO> owned(LoginUser user,List<Long> ids){List<CartDO> result=rows(user).stream().filter(x->ids.contains(x.getId())).collect(Collectors.toList());if(result.size()!=ids.size())throw exception0(404,"部分购物车商品不存在");return result;}
    private ProductTradeSkuRespDTO product(Long sku){ProductTradeSkuRespDTO value=products.list(Collections.singletonList(sku)).get(sku);if(value==null)throw exception0(409,"商品或规格已失效");return value;}
    private void available(ProductTradeSkuRespDTO product,int count){quantity(count);if(product==null)throw exception0(409,"商品或规格已失效");if(product.getStock()<count)throw exception0(409,"商品库存不足");}
    private void sameSpu(CartDO row,ProductTradeSkuRespDTO product){if(!Objects.equals(row.getSpuId(),product.getSpuId()))throw exception0(400,"不能把购物车商品替换为另一件商品");}
    private void purchase(MemberStorefrontRespDTO entry){if(!Boolean.TRUE.equals(entry.getCanPurchase()))throw exception0(403,"店铺暂不支持新增购买");}
    private void quantity(Integer count){if(count==null || count<1 || count>limits.getMaxItemQuantity())throw exception0(400,"商品数量超出当前限制");}
    private void ids(List<Long> ids){if(ids==null || ids.isEmpty() || ids.size()>200 || new HashSet<>(ids).size()!=ids.size())throw exception0(400,"购物车编号不正确");ids.forEach(this::id);}
    private void id(Long x){if(x==null || x<=0 || x>9_007_199_254_740_991L)throw exception0(400,"购物车或商品编号不正确");}
    private void changed(int count){if(count!=1)throw exception0(409,"购物车记录已变化，请重新读取");}
    private static class Snapshot {private long version;private List<CartDO> rows;}
}
