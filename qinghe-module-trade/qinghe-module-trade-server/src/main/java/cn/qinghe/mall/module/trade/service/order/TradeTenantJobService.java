package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeTenantJobCursorDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeTenantJobCursorMapper;
import cn.qinghe.mall.module.trade.service.message.TradeOrderEventPublisher;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillResultPublisher;
import cn.qinghe.mall.module.trade.service.seckill.TradeSeckillBindingCleanupService;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.RequestContextHolder;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 择取原TenantJobAspect逐租户委托/异常隔离/上下文清理职责。
 * 普通恢复/发送及秒杀结果发送/过期绑定清理四个固定任务；本地原义务发现+固定游标，不依赖system租户启用状态。
 * @author 芋道源码
 */
@Service
public class TradeTenantJobService {
    private final TradeTenantJobCursorMapper cursors;
    private final TradeOrderRecoveryService recovery;
    private final ObjectProvider<TradeOrderEventPublisher> publishers;
    private final ObjectProvider<TradeSeckillResultPublisher> seckillPublishers;
    private final ObjectProvider<TradeSeckillBindingCleanupService> bindingCleaners;
    private final TransactionTemplate write;

    public TradeTenantJobService(TradeTenantJobCursorMapper cursors,TradeOrderRecoveryService recovery,
            ObjectProvider<TradeOrderEventPublisher> publishers,ObjectProvider<TradeSeckillResultPublisher> seckillPublishers,
            ObjectProvider<TradeSeckillBindingCleanupService> bindingCleaners,PlatformTransactionManager manager) {
        this.cursors=cursors;this.recovery=recovery;this.publishers=publishers;this.seckillPublishers=seckillPublishers;
        this.bindingCleaners=bindingCleaners;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(5);
    }

    public void run(String code) {
        if(RequestContextHolder.getRequestAttributes()!=null || SecurityContextHolder.getContext().getAuthentication()!=null
                || TransactionSynchronizationManager.isActualTransactionActive() || XxlJobContext.getXxlJobContext()==null
                || XxlJobHelper.getJobId()<=0)throw new IllegalStateException("全租户交易任务仅允许可信XXL后台上下文");
        if(!Arrays.asList("ORDER_RECOVERY","OUTBOX_PUBLISH","SECKILL_RESULT_PUBLISH","SECKILL_BINDING_CLEANUP").contains(code))throw new IllegalArgumentException("不支持的交易任务");
        TenantContextHolder.clear();SecurityContextHolder.clearContext();clearLog();
        String trace=UUID.randomUUID().toString();MDC.put("traceId",trace);
        TradeTenantJobCursorDO cursor=null;int attempted=0,failed=0;
        try {
            int shard=XxlJobHelper.getShardIndex(),total=XxlJobHelper.getShardTotal();
            if(total<1 || total>128 || shard<0 || shard>=total)throw new IllegalArgumentException("XXL分片范围不正确");
            if("OUTBOX_PUBLISH".equals(code) && publishers.getIfAvailable()==null)throw new IllegalStateException("事件发布未启用");
            if("SECKILL_RESULT_PUBLISH".equals(code) && seckillPublishers.getIfAvailable()==null)throw new IllegalStateException("秒杀结果发布未启用");
            if("SECKILL_BINDING_CLEANUP".equals(code) && bindingCleaners.getIfAvailable()==null)throw new IllegalStateException("秒杀绑定清理未启用");
            String input=XxlJobHelper.getJobParam();
            if(input!=null && !input.isEmpty() && !"all".equals(input)) {
                if(!input.matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException("任务参数只允许all或原单个正租户编号");
                long tenant=Long.parseLong(input);
                if(tenant>9_007_199_254_740_991L)throw new IllegalArgumentException("租户编号超出范围");
                if(tenant%total==shard) {attempted++;if(!perform(code,tenant,trace))failed++;}
            } else {
                cursor=claim(code,shard,total);
                if(cursor==null)throw new IllegalStateException("当前任务分片由其他执行者持有");
                List<Long> tenants="SECKILL_BINDING_CLEANUP".equals(code)
                        ?cursors.seckillBindingTenants(cursor.getLastTenantId(),cursor.getCeilingTenantId(),shard,total)
                        :"SECKILL_RESULT_PUBLISH".equals(code)
                        ?cursors.seckillResultTenants(cursor.getLastTenantId(),cursor.getCeilingTenantId(),shard,total)
                        :cursors.tenants(cursor.getLastTenantId(),cursor.getCeilingTenantId(),shard,total);
                validate(tenants,cursor);
                if(tenants.isEmpty())advance(cursor,cursor.getCeilingTenantId());
                long started=System.nanoTime();
                for(Long tenant:tenants) {
                    // 不开无界parallelStream；轮转进度持久保存，下次从后续租户续行。
                    if(System.nanoTime()-started>=45_000_000_000L)break;
                    attempted++;if(!perform(code,tenant,trace))failed++;
                    // 失败租户也推进遍历位置，避免它长期阻塞后续租户；原业务失败预算不重置。
                    advance(cursor,tenant);
                }
                if(!tenants.isEmpty() && tenants.size()<5 && attempted==tenants.size())advance(cursor,cursor.getCeilingTenantId());
            }
            XxlJobHelper.log("trade-job={}, shard={}/{}, tenants={}, failed={}, trace={}",code,shard,total,attempted,failed,trace);
            if(failed>0)XxlJobHelper.handleFail("交易租户批次存在失败/人工记录，trace="+trace);
            else XxlJobHelper.handleSuccess("交易租户批次完成，tenants="+attempted+", trace="+trace);
        } catch(RuntimeException error) {
            XxlJobHelper.log("交易租户批次未完成 code={}, tenants={}, trace={}",code,attempted,trace);
            XxlJobHelper.handleFail("交易租户批次未完成，trace="+trace);
        } finally {
            TenantContextHolder.clear();SecurityContextHolder.clearContext();
            if(cursor!=null) {
                TradeTenantJobCursorDO owned=cursor;
                try {write.executeWithoutResult(tx -> cursors.release(owned.getJobCode(),owned.getShardIndex(),owned.getShardTotal(),owned.getLeaseOwner(),now()));}
                catch(RuntimeException pending) {
                    XxlJobHelper.log("交易任务游标释放待核对 code={}, trace={}",code,trace);
                    XxlJobHelper.handleFail("交易任务游标释放待核对，trace="+trace);
                }
            }
            clearLog();
        }
    }

    private boolean perform(String code,Long tenant,String trace) {
        MDC.put("tenantId",tenant.toString());
        try {
            return TenantUtils.execute(tenant,() -> {
                if("ORDER_RECOVERY".equals(code)) {
                    TradeOrderRecoveryService.Result result=recovery.recover(5,XxlJobHelper.getJobId(),trace);
                    XxlJobHelper.log("tenant={}, recovered={}, pending={}, failed={}, manual={}, trace={}",tenant,
                            result.getCompleted(),result.getPending(),result.getFailed(),result.getManual(),trace);
                    return result.getFailed()==0 && result.getManual()==0;
                }
                if("SECKILL_RESULT_PUBLISH".equals(code)) {
                    TradeSeckillResultPublisher publisher=seckillPublishers.getIfAvailable();
                    if(publisher==null)throw new IllegalStateException("秒杀结果发布未启用");
                    TradeSeckillResultPublisher.Result result=publisher.publish(5,trace);
                    XxlJobHelper.log("tenant={}, seckill-published={}, failed={}, manual={}, overdue={}, trace={}",tenant,
                            result.getPublished(),result.getFailed(),result.getManual(),result.getOverdue(),trace);
                    return result.getFailed()==0 && result.getManual()==0 && result.getOverdue()==0;
                }
                if("SECKILL_BINDING_CLEANUP".equals(code)) {
                    TradeSeckillBindingCleanupService cleaner=bindingCleaners.getIfAvailable();
                    if(cleaner==null)throw new IllegalStateException("秒杀绑定清理未启用");
                    TradeSeckillBindingCleanupService.Result result=cleaner.recover(5,trace);
                    XxlJobHelper.log("tenant={}, seckill-bindings-closed={}, skipped={}, failed={}, manual={}, trace={}",tenant,
                            result.getClosed(),result.getSkipped(),result.getFailed(),result.getManual(),trace);
                    return result.getFailed()==0 && result.getManual()==0;
                }
                TradeOrderEventPublisher publisher=publishers.getIfAvailable();
                if(publisher==null)throw new IllegalStateException("事件发布未启用");
                TradeOrderEventPublisher.Result result=publisher.publish(5,trace);
                XxlJobHelper.log("tenant={}, published={}, failed={}, manual={}, overdue={}, trace={}",tenant,
                        result.getPublished(),result.getFailed(),result.getManual(),result.getOverdue(),trace);
                return result.getFailed()==0 && result.getManual()==0 && result.getOverdue()==0;
            });
        } catch(RuntimeException error) {
            XxlJobHelper.log("交易租户执行失败 tenant={}, code={}, trace={}",tenant,code,trace);return false;
        } finally {
            TenantContextHolder.clear();SecurityContextHolder.clearContext();
            MDC.remove("tenantId");MDC.remove("orderId");MDC.remove("eventId");MDC.remove("requestNo");MDC.remove("requestId");MDC.put("traceId",trace);
        }
    }
    private TradeTenantJobCursorDO claim(String code,int shard,int total) {
        return write.execute(tx -> {
            LocalDateTime instant=now();cursors.initialize(code,shard,total,instant);
            TradeTenantJobCursorDO row=cursors.lock(code,shard,total);
            if(row==null)throw new IllegalStateException("交易任务游标未确认");
            if(row.getLeaseUntil()!=null && row.getLeaseUntil().isAfter(instant))return null;
            long after=row.getLastTenantId(),ceiling=row.getCeilingTenantId();
            if(after>=ceiling) {
                Long maximum="SECKILL_BINDING_CLEANUP".equals(code)?cursors.seckillBindingCeiling()
                        :"SECKILL_RESULT_PUBLISH".equals(code)?cursors.seckillResultCeiling():cursors.ceiling();
                if(maximum==null || maximum<0 || maximum>9_007_199_254_740_991L)throw new IllegalStateException("交易租户边界不正确");
                after=0;ceiling=maximum;
            }
            String owner=UUID.randomUUID().toString();
            if(cursors.claim(code,shard,total,after,ceiling,owner,instant.plusSeconds(180),instant)!=1)
                throw new IllegalStateException("交易任务分片领取已变化");
            row.setLastTenantId(after);row.setCeilingTenantId(ceiling);row.setLeaseOwner(owner);return row;
        });
    }
    private void advance(TradeTenantJobCursorDO row,Long tenant) {
        write.executeWithoutResult(tx -> {
            LocalDateTime instant=now();
            if(cursors.advance(row.getJobCode(),row.getShardIndex(),row.getShardTotal(),row.getLeaseOwner(),tenant,instant.plusSeconds(180),instant)!=1)
                throw new IllegalStateException("交易任务游标租约或进度已变化");
        });
        row.setLastTenantId(tenant);
    }
    private void validate(List<Long> tenants,TradeTenantJobCursorDO row) {
        if(tenants==null || tenants.size()>5)throw new IllegalStateException("交易租户批次大小不正确");
        long previous=row.getLastTenantId();
        for(Long tenant:tenants) {
            if(tenant==null || tenant<=previous || tenant>row.getCeilingTenantId() || tenant%row.getShardTotal()!=row.getShardIndex())
                throw new IllegalStateException("交易租户游标结果不正确");
            previous=tenant;
        }
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
    private void clearLog(){MDC.remove("tenantId");MDC.remove("orderId");MDC.remove("eventId");MDC.remove("traceId");MDC.remove("requestNo");MDC.remove("requestId");}
}
