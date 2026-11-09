package cn.qinghe.mall.module.product.service.search;

import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.dal.mysql.search.ProductSearchControlMapper;
import cn.qinghe.mall.module.product.dal.mysql.search.ProductSearchRoundMapper;
import cn.qinghe.mall.module.product.framework.search.ProductSearchIndex;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.util.*;

/** 商品搜索固定恢复流程：本库捕获→事务外原文发送→显式刷新/数量证明→本库确认。
 * 沿用既有缓存同步Service的短事务ACK模式，不建立通用重试或事件框架。
 */
@Service
@ConditionalOnProperty(prefix="qinghe.product.search",name="enabled",havingValue="true")
public class ProductSearchSynchronizer {
    public enum Step { CAPTURED, SENT, CONFIRMED, CURRENT, STALE }
    private final ProductSearchControlMapper control;
    private final ProductSearchRoundMapper rounds;
    private final ProductSearchChanges changes;
    private final ProductSearchSnapshots snapshots;
    private final ProductSearchIndex index;
    private final TransactionTemplate write;
    public ProductSearchSynchronizer(ProductSearchControlMapper control,ProductSearchRoundMapper rounds,ProductSearchChanges changes,
            ProductSearchSnapshots snapshots,ProductSearchIndex index,PlatformTransactionManager manager) {
        this.control=control;this.rounds=rounds;this.changes=changes;this.snapshots=snapshots;this.index=index;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }

    /** 一次只推进一个阶段/一页；抛错保留原游标，下次仍按原版本重试。无启动期调用。 */
    public Step synchronize() {
        Long tenant=context();ProductSearchIndex.Target target=index.target();
        ProductSearchRoundMapper.Row plan=plan(tenant,target);
        if(plan==null)return Step.CURRENT;
        ProductSearchRoundMapper.Row next=BeanUtils.toBean(plan,ProductSearchRoundMapper.Row.class);
        if("CAPTURE".equals(plan.getPhase())) {
            ProductSearchSnapshots.Page page=snapshots.capture(plan.getVersion(),plan.getCaptureAfter());
            next.setCaptureAfter(page.getNextId());next.setCapturedCount(Math.addExact(plan.getCapturedCount(),page.getDocuments().size()));
            if(page.isExhausted())next.setPhase("SEND");
            return advance(plan,next,false)?Step.CAPTURED:Step.STALE;
        }
        if("SEND".equals(plan.getPhase())) {
            ProductSearchSnapshots.Page page=snapshots.saved(plan.getVersion(),plan.getSendAfter());
            int sent=0;long last=plan.getSendAfter(),started=System.nanoTime();
            for(ProductSearchDocument document:page.getDocuments()) {
                if(sent>=5 || System.nanoTime()-started>=45000000000L)break;
                if(document.getId()<=last || document.getId()>plan.getCaptureAfter())throw incomplete();
                if(index.putSnapshot(document,target)==ProductSearchIndex.WriteResult.SUPERSEDED)
                    throw new IllegalStateException("索引已有更高版本，需要核对原同步轮次");
                last=document.getId();sent++;
            }
            long count=Math.addExact(plan.getSentCount(),sent);
            if(count>plan.getCapturedCount() || (count==plan.getCapturedCount())!=(last==plan.getCaptureAfter())
                    || sent==0 && count!=plan.getCapturedCount())throw incomplete();
            if(!target.equals(index.target()))throw new IllegalStateException("发送期间搜索索引身份改变，不能确认进度");
            next.setSendAfter(last);next.setSentCount(count);
            if(count==plan.getCapturedCount())next.setPhase("REFRESH");
            return advance(plan,next,false)?Step.SENT:Step.STALE;
        }
        if("REFRESH".equals(plan.getPhase())) {
            index.confirm(target,tenant,plan.getVersion(),plan.getCapturedCount());
            next.setPhase("DONE");
            return advance(plan,next,true)?Step.CONFIRMED:Step.STALE;
        }
        throw incomplete();
    }

    private ProductSearchRoundMapper.Row plan(Long tenant,ProductSearchIndex.Target target) {
        return write.execute(tx->{
            // control→round固定顺序。业务事务只在完成商品写入后取control，不读round。
            control.ensure(tenant);ProductSearchControlMapper.State state=changes.state(tenant);
            if(state==null)throw incomplete();
            if(state.getChangeVersion()==0) {
                if(control.changed(tenant)!=1)throw incomplete();state=changes.state(tenant);
            }
            ProductSearchRoundMapper.Row current=rounds.lock(tenant);
            if(current!=null) {
                validate(current);if(!tenant.equals(current.getTenantId()))throw incomplete();
                if(!binding(current,target))throw new IllegalStateException("搜索索引身份改变，需要显式离线重建");
                if(current.getVersion()>state.getChangeVersion())throw incomplete();
                if(current.getVersion().equals(state.getChangeVersion())) {
                    if(!"DONE".equals(current.getPhase()))return current;
                    if(!Boolean.TRUE.equals(state.getInitialized()) || !current.getVersion().equals(state.getSyncedVersion()))throw incomplete();
                    return null;
                }
            }
            ProductSearchRoundMapper.Row start=newRound(tenant,state.getChangeVersion(),target);
            if(current==null ? rounds.insert(start)!=1 : rounds.save(current,start)!=1)throw incomplete();
            return start;
        });
    }

    /** 受控任务的离线准备：新alias与新物理索引隔离迟到的旧工作者；不删除旧索引或设置ready。 */
    public boolean prepareRebuild(Long expectedVersion,String oldAlias,String oldUuid) {
        Long tenant=context();
        if(expectedVersion==null || expectedVersion<=0 || expectedVersion>=9007199254740991L
                || oldAlias==null || !oldAlias.matches("qinghe-product-[a-z0-9][a-z0-9-]{0,63}")
                || oldUuid==null || !oldUuid.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("离线重建需要原版本和原索引身份");
        ProductSearchIndex.Target target=index.target();
        if(Boolean.TRUE.equals(write.execute(tx->rebuildSource(tenant,expectedVersion,oldAlias,oldUuid,target)==null)))return false;
        // 新目标内本租户必须为空。只证明索引，不持有本库锁；其他已迁移租户可以保留。
        index.confirm(target,tenant,expectedVersion,0L);
        return Boolean.TRUE.equals(write.execute(tx->{
            ProductSearchRoundMapper.Row old=rebuildSource(tenant,expectedVersion,oldAlias,oldUuid,target);
            if(old==null)return false;
            if(rounds.beginRebuild(tenant,expectedVersion)!=1 || rounds.save(old,newRound(tenant,expectedVersion+1,target))!=1)throw incomplete();
            return true;
        }));
    }
    /** 只在短事务中调用，沿用control→round顺序。已切到同一目标只读返回，不重置正在推进的轮次。 */
    private ProductSearchRoundMapper.Row rebuildSource(Long tenant,Long expected,String oldAlias,String oldUuid,ProductSearchIndex.Target target) {
        control.ensure(tenant);ProductSearchControlMapper.State state=changes.state(tenant);
        ProductSearchRoundMapper.Row old=rounds.lock(tenant);validate(old);
        if(state==null || !tenant.equals(old.getTenantId()))throw incomplete();
        if(binding(old,target))return null;
        if(!expected.equals(state.getChangeVersion()) || old.getVersion()>expected || !oldAlias.equals(old.getAliasName()) || !oldUuid.equals(old.getIndexUuid())
                || oldAlias.equals(target.getAlias()) || oldUuid.equals(target.getUuid()) || old.getPhysicalName().equals(target.getPhysical()))
            throw new IllegalStateException("原目录已变化或重建目标不是新的独立索引别名");
        return old;
    }
    private ProductSearchRoundMapper.Row newRound(Long tenant,Long version,ProductSearchIndex.Target target) {
        ProductSearchRoundMapper.Row row=new ProductSearchRoundMapper.Row();
        row.setTenantId(tenant);row.setRoundNo(UUID.randomUUID().toString());row.setVersion(version);
        row.setAliasName(target.getAlias());row.setPhysicalName(target.getPhysical());row.setIndexUuid(target.getUuid());
        row.setPhase("CAPTURE");row.setCaptureAfter(0L);row.setSendAfter(0L);row.setCapturedCount(0L);row.setSentCount(0L);
        return row;
    }

    private boolean advance(ProductSearchRoundMapper.Row plan,ProductSearchRoundMapper.Row next,boolean confirmed) {
        if(!Objects.equals(context(),plan.getTenantId()))throw incomplete();validate(next);
        return Boolean.TRUE.equals(write.execute(tx->{
            control.ensure(plan.getTenantId());ProductSearchControlMapper.State state=changes.state(plan.getTenantId());
            ProductSearchRoundMapper.Row current=rounds.lock(plan.getTenantId());
            if(current==null || state==null || !plan.getVersion().equals(state.getChangeVersion()) || !same(plan,current))return false;
            if(confirmed && (!"REFRESH".equals(plan.getPhase()) || !"DONE".equals(next.getPhase())
                    || !plan.getCapturedCount().equals(plan.getSentCount()) || !plan.getCaptureAfter().equals(plan.getSendAfter())))throw incomplete();
            if(rounds.save(plan,next)!=1 || confirmed && rounds.confirm(plan.getTenantId(),plan.getVersion())!=1)throw incomplete();
            return true;
        }));
    }
    private boolean same(ProductSearchRoundMapper.Row a,ProductSearchRoundMapper.Row b) {
        return Objects.equals(a.getTenantId(),b.getTenantId()) && Objects.equals(a.getRoundNo(),b.getRoundNo())
                && Objects.equals(a.getVersion(),b.getVersion()) && Objects.equals(a.getPhase(),b.getPhase())
                && Objects.equals(a.getCaptureAfter(),b.getCaptureAfter()) && Objects.equals(a.getSendAfter(),b.getSendAfter())
                && Objects.equals(a.getCapturedCount(),b.getCapturedCount()) && Objects.equals(a.getSentCount(),b.getSentCount())
                && Objects.equals(a.getAliasName(),b.getAliasName()) && Objects.equals(a.getPhysicalName(),b.getPhysicalName()) && Objects.equals(a.getIndexUuid(),b.getIndexUuid());
    }
    private boolean binding(ProductSearchRoundMapper.Row row,ProductSearchIndex.Target target) {
        return target.getAlias().equals(row.getAliasName()) && target.getPhysical().equals(row.getPhysicalName()) && target.getUuid().equals(row.getIndexUuid());
    }
    private void validate(ProductSearchRoundMapper.Row row) {
        if(row==null || row.getTenantId()==null || row.getTenantId()<=0 || row.getTenantId()>9007199254740991L
                || row.getVersion()==null || row.getVersion()<=0 || row.getVersion()>9007199254740991L
                || row.getRoundNo()==null || !row.getRoundNo().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || row.getAliasName()==null || !row.getAliasName().matches("qinghe-product-[a-z0-9][a-z0-9-]{0,63}")
                || row.getPhysicalName()==null || !row.getPhysicalName().matches("qinghe-product-[a-z0-9][a-z0-9-]{0,111}")
                || row.getPhysicalName().equals(row.getAliasName()) || row.getIndexUuid()==null || !row.getIndexUuid().matches("[A-Za-z0-9_-]{1,64}")
                || !Arrays.asList("CAPTURE","SEND","REFRESH","DONE").contains(row.getPhase())
                || row.getCaptureAfter()==null || row.getCaptureAfter()<0 || row.getCaptureAfter()>9007199254740991L
                || row.getSendAfter()==null || row.getSendAfter()<0 || row.getSendAfter()>row.getCaptureAfter()
                || row.getCapturedCount()==null || row.getCapturedCount()<0 || row.getCapturedCount()>row.getCaptureAfter()
                || row.getSentCount()==null || row.getSentCount()<0 || row.getSentCount()>row.getCapturedCount()
                || (row.getCapturedCount()==0)!=(row.getCaptureAfter()==0) || (row.getSentCount()==0)!=(row.getSendAfter()==0)
                || "CAPTURE".equals(row.getPhase()) && row.getSentCount()!=0
                || Arrays.asList("REFRESH","DONE").contains(row.getPhase()) && (!row.getSendAfter().equals(row.getCaptureAfter()) || !row.getSentCount().equals(row.getCapturedCount())))throw incomplete();
    }
    private Long context() {
        Long tenant=TenantContextHolder.getTenantId();
        if(TransactionSynchronizationManager.isActualTransactionActive() || tenant==null || tenant<=0 || tenant>9007199254740991L || TenantContextHolder.isIgnore())
            throw new IllegalStateException("搜索同步需要事务外的可信租户");
        return tenant;
    }
    private IllegalStateException incomplete(){return new IllegalStateException("商品搜索轮次或持久进度不完整");}
}
