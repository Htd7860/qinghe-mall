package cn.qinghe.mall.module.product.service.search;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.dal.mysql.search.ProductSearchControlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** 商品搜索专用持久变更标记；不是提交后内存事件，不执行ES或新事务。 */
@Service @RequiredArgsConstructor
public class ProductSearchChanges {
    private final ProductSearchControlMapper mapper;
    public void mark(Long tenant) {
        MybatisUtils.requireWriteTransaction();
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || !Objects.equals(tenant,TenantContextHolder.getTenantId()))throw new IllegalStateException("搜索变更需要原写事务的可信租户");
        mapper.ensure(tenant);
        if(mapper.changed(tenant)!=1)throw new IllegalStateException("搜索变更版本已到上限或记录不完整");
    }
    /** 缺记录与未初始化均拒绝；后续查询还须跨ES调用前后同版本校验。 */
    public ProductSearchControlMapper.State state(Long tenant) {
        if(tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore()
                || !Objects.equals(tenant,TenantContextHolder.getTenantId()))throw new IllegalStateException("搜索读取缺少可信租户");
        ProductSearchControlMapper.State row=mapper.read(tenant);
        if(row==null)return null;
        if(!tenant.equals(row.getTenantId()) || row.getChangeVersion()==null || row.getChangeVersion()<0 || row.getChangeVersion()>9007199254740991L
                || row.getSyncedVersion()==null || row.getSyncedVersion()<0 || row.getSyncedVersion()>row.getChangeVersion()
                || row.getInitialized()==null || row.getUpdateTime()==null)throw new IllegalStateException("搜索变更进度不完整");
        return row;
    }
}
