package cn.qinghe.mall.module.product.service.spu;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原SKU/SPU批量API读取和转换职责，MEMBER只读本店上架启用SKU，不使用员工owner范围。
 * @author 芋道源码
 */
@Service @RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class ProductTradeReadService {
    private final ProductSkuMapper skus;
    private final ProductSpuMapper spus;
    public List<ProductTradeSkuRespDTO> list(List<Long> ids) {
        Long tenant=tenant();
        if(ids==null || ids.isEmpty() || ids.size()>200 || new HashSet<>(ids).size()!=ids.size()
                || ids.stream().anyMatch(x->x==null || x<=0))throw exception0(400,"商品编号不正确");
        return project(skus.selectList(query(tenant,null).in(ProductSkuDO::getId,ids).orderByAsc(ProductSkuDO::getId)),tenant);
    }
    public PageResult<ProductTradeSkuRespDTO> page(String keyword,PageParam page,Long spuId) {
        Long tenant=tenant();
        if(keyword!=null && (keyword.length()>64 || keyword.chars().anyMatch(Character::isISOControl))
                || page.getPageSize()==null || page.getPageSize()<1 || page.getPageSize()>20
                || page.getPageNo()==null || page.getPageNo()<1 || page.getPageNo()>10000)throw exception0(400,"商品查询条件不正确");
        if(spuId!=null && spuId<=0)throw exception0(400,"商品编号不正确");
        LambdaQueryWrapperX<ProductSkuDO> q=query(tenant,keyword);if(spuId!=null)q.eq(ProductSkuDO::getSpuId,spuId);
        PageResult<ProductSkuDO> rows=skus.selectPage(page,q.orderByDesc(ProductSkuDO::getId));
        return new PageResult<>(rows.getTotal(),project(rows.getList(),tenant));
    }
    private LambdaQueryWrapperX<ProductSkuDO> query(Long tenant,String keyword) {
        LambdaQueryWrapperX<ProductSkuDO> q=new LambdaQueryWrapperX<ProductSkuDO>().eq(ProductSkuDO::getTenantId,tenant).eq(ProductSkuDO::getStatus,0);
        String exists="EXISTS (SELECT 1 FROM product_spu p WHERE p.tenant_id={0} AND p.id=product_sku.spu_id AND p.deleted=b'0' AND p.status=1";
        if(keyword==null || keyword.trim().isEmpty())q.apply(exists+")",tenant);
        else q.apply(exists+" AND p.name LIKE {1})",tenant,"%"+keyword.trim()+"%");
        return q;
    }
    private List<ProductTradeSkuRespDTO> project(List<ProductSkuDO> rows,Long tenant) {
        if(rows.isEmpty())return Collections.emptyList();
        Map<Long,ProductSpuDO> parents=spus.selectList(new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getTenantId,tenant)
                .eq(ProductSpuDO::getStatus,1).in(ProductSpuDO::getId,rows.stream().map(ProductSkuDO::getSpuId).collect(Collectors.toList())))
                .stream().collect(Collectors.toMap(ProductSpuDO::getId,x->x));
        List<ProductTradeSkuRespDTO> result=new ArrayList<>();
        for(ProductSkuDO sku:rows) {
            ProductSpuDO spu=parents.get(sku.getSpuId());if(spu==null)continue;
            if(sku.getPrice()==null || sku.getPrice()<=0 || sku.getPrice()>2_000_000_000 || sku.getStock()==null || sku.getStock()<0
                    || spu.getAuthVersion()==null || sku.getAuthVersion()==null || spu.getCategoryId()==null || spu.getDeliveryTemplateId()==null)throw exception0(503,"商品销售数据不完整");
            ProductTradeSkuRespDTO dto=new ProductTradeSkuRespDTO();dto.setTenantId(tenant);dto.setSkuId(sku.getId());dto.setSpuId(spu.getId());
            dto.setCategoryId(spu.getCategoryId());dto.setDeliveryTemplateId(spu.getDeliveryTemplateId());dto.setSpuVersion(spu.getAuthVersion());dto.setSkuVersion(sku.getAuthVersion());
            dto.setName(spu.getName());dto.setPicUrl(sku.getPicUrl()==null || sku.getPicUrl().isEmpty()?spu.getPicUrl():sku.getPicUrl());
            dto.setPrice(sku.getPrice());dto.setStock(sku.getStock());dto.setWeight(sku.getWeight()==null?null:sku.getWeight().toPlainString());
            dto.setProperties(BeanUtils.toBean(sku.getProperties()==null?Collections.emptyList():sku.getProperties(),ProductTradeSkuRespDTO.Property.class));result.add(dto);
        }
        return result;
    }
    private Long tenant() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || !UserTypeEnum.MEMBER.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0
                || TenantContextHolder.isIgnore() || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"商品销售读取需要当前会员身份");
        return actor.getTenantId();
    }
}
