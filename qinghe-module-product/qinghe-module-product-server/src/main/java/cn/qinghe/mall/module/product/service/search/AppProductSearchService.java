package cn.qinghe.mall.module.product.service.search;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.product.controller.app.spu.vo.*;
import cn.qinghe.mall.module.product.dal.mysql.search.ProductSearchRoundMapper;
import cn.qinghe.mall.module.product.framework.search.ProductSearchIndex;
import cn.qinghe.mall.module.product.framework.storefront.ProductStorefrontAccess;
import cn.qinghe.mall.module.product.service.spu.AppProductSpuService;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Validator;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原ES分页职责，复用已存在的真实Host店铺解析及销售VO；计数也受目录版本屏障保护。 */
@Service
public class AppProductSearchService {
    private final ObjectProvider<ProductSearchIndex> indexes;
    private final ProductSearchRoundMapper rounds;
    private final ProductStorefrontAccess access;
    private final AppProductSpuService products;
    private final Validator validator;
    private final TransactionTemplate read;
    public AppProductSearchService(ObjectProvider<ProductSearchIndex> indexes,ProductSearchRoundMapper rounds,ProductStorefrontAccess access,
            AppProductSpuService products,Validator validator,PlatformTransactionManager manager) {
        this.indexes=indexes;this.rounds=rounds;this.access=access;this.products=products;this.validator=validator;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public PageResult<AppProductSpuRespVO> page(HttpServletRequest request,String store,AppProductSpuPageReqVO input) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("公开搜索不能嵌套本地事务");
        if(input==null)throw exception0(400,"搜索条件不正确");
        AppProductSpuPageReqVO query=BeanUtils.toBean(input,AppProductSpuPageReqVO.class);
        ProductSearchQuery.validate(validator,query);
        ProductSearchIndex index=indexes.getIfAvailable();if(index==null)throw exception0(503,"商品搜索尚未启用");
        MemberStorefrontRespDTO entry=access.resolve(request,store);
        return TenantUtils.execute(entry.getTenantId(),()->{
            ProductSearchIndex.Target target;
            try {target=index.target();}catch(RuntimeException unavailable){throw updating();}
            ProductSearchRoundMapper.Row before=read.execute(tx->ready(entry.getTenantId(),target));
            ProductSearchIndex.SearchPage page;
            try {page=index.search(target,entry.getTenantId(),before.getVersion(),query,validator);}
            catch(RuntimeException unavailable){throw updating();}
            if(page.getTotal()>before.getCapturedCount())throw updating();
            List<AppProductSpuRespVO> result=read.execute(tx->{
                same(before,ready(entry.getTenantId(),target));
                return products.searchRows(entry,page.getIds());
            });
            // 此时数据库事务已结束；不在RR快照中重读来假装看到最新提交。
            try {if(!target.equals(index.target()))throw updating();}catch(RuntimeException unavailable){throw updating();}
            read.executeWithoutResult(tx->same(before,ready(entry.getTenantId(),target)));
            return new PageResult<>(page.getTotal(),result);
        });
    }
    private ProductSearchRoundMapper.Row ready(Long tenant,ProductSearchIndex.Target target) {
        ProductSearchRoundMapper.Row row=rounds.ready(tenant,target.getAlias(),target.getPhysical(),target.getUuid());
        if(row==null || !tenant.equals(row.getTenantId()) || row.getVersion()==null || row.getVersion()<=0 || row.getVersion()>9007199254740991L
                || row.getRoundNo()==null || !row.getRoundNo().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || row.getCapturedCount()==null || row.getCapturedCount()<0 || row.getCapturedCount()>9007199254740991L
                || !row.getCapturedCount().equals(row.getSentCount()) || !Objects.equals(row.getCaptureAfter(),row.getSendAfter()))throw updating();
        return row;
    }
    private void same(ProductSearchRoundMapper.Row a,ProductSearchRoundMapper.Row b) {
        if(!a.getRoundNo().equals(b.getRoundNo()) || !a.getVersion().equals(b.getVersion()) || !a.getCapturedCount().equals(b.getCapturedCount()))throw updating();
    }
    private cn.qinghe.mall.framework.common.exception.ServiceException updating(){return exception0(503,"商品搜索资料更新中，请稍后重试");}
}
