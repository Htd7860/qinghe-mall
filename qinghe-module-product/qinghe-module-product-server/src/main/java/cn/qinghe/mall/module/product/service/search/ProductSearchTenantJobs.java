package cn.qinghe.mall.module.product.service.search;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.product.dal.mysql.search.ProductSearchJobCursorMapper;
import com.xxl.job.core.context.*;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.util.*;

/** 择取yudao TenantJobAspect的逐租户委托、结果隔离和finally清理；固定有界分页替代全量parallelStream。
 * @author 芋道源码
 */
@Service
@ConditionalOnProperty(prefix="qinghe.product.search",name="enabled",havingValue="true")
public class ProductSearchTenantJobs {
    private final ProductSearchJobCursorMapper cursors;
    private final ProductSearchSynchronizer synchronizer;
    private final TransactionTemplate write;
    public ProductSearchTenantJobs(ProductSearchJobCursorMapper cursors,ProductSearchSynchronizer synchronizer,PlatformTransactionManager manager) {
        this.cursors=cursors;this.synchronizer=synchronizer;write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }
    public void run(boolean rebuild) {
        if(RequestContextHolder.getRequestAttributes()!=null || SecurityContextHolder.getContext().getAuthentication()!=null
                || TransactionSynchronizationManager.isActualTransactionActive() || XxlJobContext.getXxlJobContext()==null || XxlJobHelper.getJobId()<=0)
            throw new IllegalStateException("搜索全租户操作仅允许可信XXL后台上下文");
        TenantContextHolder.clear();SecurityContextHolder.clearContext();MDC.clear();String trace=UUID.randomUUID().toString();MDC.put("traceId",trace);
        int attempted=0,failed=0;
        try {
            int shard=XxlJobHelper.getShardIndex(),total=XxlJobHelper.getShardTotal();
            if(total<1 || total>128 || shard<0 || shard>=total)throw new IllegalArgumentException("XXL分片不正确");
            String input=XxlJobHelper.getJobParam();
            if(rebuild) {
                if(total!=1 || shard!=0 || input==null)throw new IllegalArgumentException("离线重建仅允许单分片和明确原租户/版本/索引");
                String[] p=input.split(":",-1);if(p.length!=4)throw new IllegalArgumentException("重建参数需要tenant:version:oldAlias:oldUuid");
                Long tenant=id(p[0]),version=id(p[1]);attempted++;
                MDC.put("tenantId",tenant.toString());
                boolean prepared=TenantUtils.execute(tenant,()->synchronizer.prepareRebuild(version,p[2],p[3]));
                XxlJobHelper.log("product-search rebuild-prepared={}, tenant={}, trace={}",prepared,tenant,trace);
            } else if(input!=null && !input.isEmpty() && !"all".equals(input)) {
                Long tenant=id(input);if(tenant%total==shard){attempted++;if(!perform(tenant,trace))failed++;}
            } else {
                ProductSearchJobCursorMapper.Row cursor=position(shard,total);
                List<Long> tenants=cursors.tenants(cursor.getLastTenantId(),cursor.getCeilingTenantId(),shard,total);
                if(tenants==null || tenants.size()>5)throw new IllegalStateException("搜索租户批次不完整");
                long previous=cursor.getLastTenantId();
                for(Long tenant:tenants) {
                    if(tenant==null || tenant<=previous || tenant>cursor.getCeilingTenantId() || tenant%total!=shard)throw new IllegalStateException("搜索租户游标归属不正确");
                    previous=tenant;
                }
                if(tenants.isEmpty())advance(cursor,cursor.getCeilingTenantId());
                long started=System.nanoTime();
                for(Long tenant:tenants) {
                    if(System.nanoTime()-started>=45000000000L)break;
                    attempted++;if(!perform(tenant,trace))failed++;
                    // 失败也轮转到下一个租户；原轮次和快照不清除，下一轮继续恢复。
                    advance(cursor,tenant);
                }
                if(!tenants.isEmpty() && tenants.size()<5 && attempted==tenants.size())advance(cursor,cursor.getCeilingTenantId());
            }
            XxlJobHelper.log("product-search tenants={}, failed={}, shard={}/{}, trace={}",attempted,failed,shard,total,trace);
            if(failed>0)XxlJobHelper.handleFail("搜索同步存在待处理租户，trace="+trace);
            else XxlJobHelper.handleSuccess("搜索任务本批执行完成，不等于全部索引追平，trace="+trace);
        } catch(RuntimeException pending) {
            XxlJobHelper.log("product-search batch-pending tenants={}, type={}, trace={}",attempted,pending.getClass().getSimpleName(),trace);
            XxlJobHelper.handleFail("搜索任务未完成，保留原进度，trace="+trace);
        } finally {TenantContextHolder.clear();SecurityContextHolder.clearContext();MDC.clear();}
    }
    private boolean perform(Long tenant,String trace) {
        MDC.put("tenantId",tenant.toString());
        try {
            ProductSearchSynchronizer.Step step=TenantUtils.execute(tenant,synchronizer::synchronize);
            XxlJobHelper.log("product-search tenant={}, step={}, trace={}",tenant,step,trace);return true;
        } catch(RuntimeException pending) {
            XxlJobHelper.log("product-search tenant={}, pending={}, trace={}",tenant,pending.getClass().getSimpleName(),trace);return false;
        } finally {MDC.remove("tenantId");}
    }
    private ProductSearchJobCursorMapper.Row position(int shard,int total) {
        return write.execute(tx->{
            cursors.ensure(shard,total);ProductSearchJobCursorMapper.Row row=cursors.lock(shard,total);validate(row,shard,total);
            if(row.getLastTenantId()>=row.getCeilingTenantId()) {
                Long ceiling=cursors.ceiling();if(ceiling==null || ceiling<0 || ceiling>9007199254740991L)throw new IllegalStateException("搜索租户扫描上界不正确");
                if(cursors.advance(row,0L,ceiling)!=1)throw new IllegalStateException("搜索游标已变化");
                row.setLastTenantId(0L);row.setCeilingTenantId(ceiling);row.setVersion(row.getVersion()+1);
            }
            return row;
        });
    }
    private void advance(ProductSearchJobCursorMapper.Row row,Long after) {
        if(after<row.getLastTenantId() || after>row.getCeilingTenantId())throw new IllegalStateException("搜索游标不能倒退或越界");
        Integer changed=write.execute(tx->cursors.advance(row,after,row.getCeilingTenantId()));
        if(!Integer.valueOf(1).equals(changed))throw new IllegalStateException("搜索任务游标竞争，下次从持久位置恢复");
        row.setLastTenantId(after);row.setVersion(row.getVersion()+1);
    }
    private void validate(ProductSearchJobCursorMapper.Row row,int shard,int total) {
        if(row==null || !Integer.valueOf(shard).equals(row.getShardIndex()) || !Integer.valueOf(total).equals(row.getShardTotal())
                || row.getLastTenantId()==null || row.getLastTenantId()<0 || row.getCeilingTenantId()==null
                || row.getCeilingTenantId()<row.getLastTenantId() || row.getCeilingTenantId()>9007199254740991L
                || row.getVersion()==null || row.getVersion()<0 || row.getVersion()>=9007199254740991L)throw new IllegalStateException("搜索任务游标不完整");
    }
    private Long id(String text) {
        if(text==null || !text.matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException("需要单个正编号");
        Long value=Long.parseLong(text);if(value>9007199254740991L)throw new IllegalArgumentException("编号超出范围");return value;
    }
}
