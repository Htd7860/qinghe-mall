package cn.qinghe.mall.module.promotion.dal.redis.seckill;

import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillPoolReturnDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillPoolReturnMapper;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillRedisEpochMapper;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.ZoneOffset;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 复用现有RedisDAO模式；首次运行hash只能在原许可窗口内建立，尚不接单。 */
@Repository
public class SeckillInitialRuntimeRedisDAO {
    private static final DefaultRedisScript<Long> INITIAL;
    private static final DefaultRedisScript<Long> CONTROL;
    static {
        INITIAL=new DefaultRedisScript<>();INITIAL.setLocation(new ClassPathResource("lua/seckill-initial-runtime.lua"));INITIAL.setResultType(Long.class);
        CONTROL=new DefaultRedisScript<>();CONTROL.setLocation(new ClassPathResource("lua/seckill-runtime-control.lua"));CONTROL.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final SeckillPublishedManifestRedisDAO manifests;
    private final SeckillPoolReturnMapper returns;
    private final SeckillRedisEpochMapper epochs;
    private final String prefix;
    public SeckillInitialRuntimeRedisDAO(StringRedisTemplate redis,SeckillPublishedManifestRedisDAO manifests,SeckillPoolReturnMapper returns,SeckillRedisEpochMapper epochs,
            @Value("${qinghe.redis.key-prefix}") String prefix) {
        if (prefix==null || prefix.length()>128 || !prefix.matches("[A-Za-z0-9:_-]+") || prefix.endsWith(":"))
            throw new IllegalArgumentException("Redis Key前缀配置不合法");
        this.redis=Objects.requireNonNull(redis);this.manifests=Objects.requireNonNull(manifests);this.returns=Objects.requireNonNull(returns);this.epochs=Objects.requireNonNull(epochs);this.prefix=prefix;
    }
    public void installPaused(SeckillRedisEpochDO receipt) {
        Long result=execute(receipt,"INSTALL");
        if (result==null || result!=1L && result!=2L) throw exception0(503,"原运行库存初始化未确认，窗口不会自动续期，请按原epoch核查");
    }
    public boolean pausedMatches(SeckillRedisEpochDO receipt) {
        Long result=execute(receipt,"MATCH");
        if (Long.valueOf(0).equals(result)) return false;
        if (!Long.valueOf(2).equals(result)) throw exception0(503,"原运行库存已变化或不完整，不能重置");
        return true;
    }
    private Long execute(SeckillRedisEpochDO receipt,String mode) {
        return execute(receipt,mode,null);
    }
    private Long execute(SeckillRedisEpochDO receipt,String mode,SeckillPoolReturnDO returned) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("运行库存不能在数据库事务内初始化");
        boolean initializing=mode.equals("INSTALL") || mode.equals("MATCH");
        if (receipt==null || !(initializing ? Integer.valueOf(15).equals(receipt.getStatus()) :
                mode.equals("CLOSE") || mode.equals("RETURN_POOL") ? Integer.valueOf(30).equals(receipt.getStatus()) : Arrays.asList(15,20).contains(receipt.getStatus())) || receipt.getInitDeadline()==null
                || receipt.getInitToken()==null || !receipt.getInitToken().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw exception0(409,"原运行库存初始化许可不完整");
        String seed=manifests.expectedPausedSeed(receipt);
        SeckillPublishedManifest definition=JsonUtils.parseObject(receipt.getDefinitionSnapshot(),SeckillPublishedManifest.class);
        long deadline=receipt.getInitDeadline().toInstant(ZoneOffset.UTC).toEpochMilli();
        if (deadline<=0 || deadline>definition.getEndAt()) throw exception0(503,"原初始化期限不正确");
        String stem=prefix+":seckill:{"+receipt.getTenantId()+":"+receipt.getActivityId()+":"+receipt.getGeneration()+"}:epoch:1:";
        List<String> args=new ArrayList<>(610);Collections.addAll(args,mode,receipt.getInitToken(),Long.toString(deadline),receipt.getDefinitionDigest(),
                definition.getEndAt().toString(),Integer.toString(definition.getSkus().size()),seed,definition.getStartAt().toString(),
                definition.getSingleLimit().toString(),definition.getMemberLimit().toString());
        for (SeckillPublishedManifest.Sku sku:definition.getSkus()) {
            Collections.addAll(args,sku.getSkuId().toString(),sku.getPlannedQuantity().toString(),sku.getPriceCent().toString());
        }
        if (mode.equals("RETURN_POOL")) {
            if (returned==null || !Objects.equals(returned.getTenantId(),receipt.getTenantId())
                    || !Objects.equals(returned.getActivityId(),receipt.getActivityId()) || !Objects.equals(returned.getGeneration(),receipt.getGeneration())
                    || !Objects.equals(returned.getRedisEpoch(),receipt.getEpoch()) || !"CONFIRMED".equals(returned.getState())
                    || !Arrays.asList("PENDING","SYNCED").contains(returned.getCacheState()) || returned.getTargetReturned()==null || returned.getTargetReturned()<=0)
                throw exception0(409,"原归还凭证与停止epoch不一致");
            Collections.addAll(args,returned.getSkuId().toString(),returned.getTargetReturned().toString());
        }
        return redis.execute(initializing?INITIAL:CONTROL,Arrays.asList(stem+"seed",stem+"runtime"),args.toArray());
    }
    public void open(SeckillRedisEpochDO receipt) {
        Long result=execute(receipt,"OPEN");
        if (result==null || result!=1L && result!=2L) throw exception0(503,"原运行库存尚未就绪或初始化窗口尚未结束，请按原epoch查询");
    }
    public boolean openMatches(SeckillRedisEpochDO receipt) {
        Long result=execute(receipt,"OPEN_MATCH");
        if (Long.valueOf(0).equals(result)) return false;
        if (!Long.valueOf(2).equals(result)) throw exception0(503,"原运行库存状态需核查，不能重新填充");
        return true;
    }
    public void close(SeckillRedisEpochDO receipt) {
        if(closedBeforeRuntime(receipt))return;
        Long result=execute(receipt,"CLOSE");
        if (result==null || result!=1L && result!=2L) throw exception0(503,"数据库已停止新接单，Redis关闭仍待确认，请按原epoch重查");
    }
    /** 只从已确认原回执/流水读取累计目标，不接受调用方自由指定数量。 */
    public void returnPool(SeckillRedisEpochDO receipt,String returnNo) {
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || TenantContextHolder.isIgnore() || receipt==null || !Objects.equals(tenant,receipt.getTenantId())
                || returnNo==null || !returnNo.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw exception0(409,"需要可信租户及原归还凭证");
        SeckillPoolReturnDO proof=returns.confirmedProof(tenant,returnNo);
        if (proof==null) throw exception0(409,"归还尚未有实际商品回执及本库流水");
        if(!Objects.equals(proof.getActivityId(),receipt.getActivityId()) || !Objects.equals(proof.getGeneration(),receipt.getGeneration())
                || !Objects.equals(proof.getRedisEpoch(),receipt.getEpoch()))throw exception0(409,"原归还不属于该停止代次");
        if(closedBeforeRuntime(receipt))return; // 未曾发放运行许可，没有运行库存投影；绝不补建Key。
        Long result=execute(receipt,"RETURN_POOL",proof);
        if (result==null || result!=1L && result!=2L) throw exception0(503,"库存已归还，原关闭池缓存仍待同步，请按原号恢复");
    }
    private boolean closedBeforeRuntime(SeckillRedisEpochDO receipt) {
        if(receipt==null || !Integer.valueOf(40).equals(receipt.getStatus()))return false;
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("原未运行关闭证明必须在数据库事务外读取");
        Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || TenantContextHolder.isIgnore() || !Objects.equals(tenant,receipt.getTenantId()))throw exception0(409,"需要可信原租户");
        SeckillRedisEpochDO current=epochs.initial(tenant,receipt.getActivityId(),receipt.getGeneration());
        if(current==null || !Integer.valueOf(40).equals(current.getStatus()) || current.getInitToken()!=null || current.getInitDeadline()!=null
                || receipt.getInitToken()!=null || receipt.getInitDeadline()!=null || !"NOT_REQUIRED".equals(current.getStopCacheState())
                || !Objects.equals(current.getEpoch(),receipt.getEpoch()) || !Objects.equals(current.getPublishRequestId(),receipt.getPublishRequestId())
                || !Objects.equals(current.getDefinitionDigest(),receipt.getDefinitionDigest()))throw exception0(409,"原未运行关闭凭证未确认，不能省略运行缓存关闭");
        manifests.expectedPausedSeed(current); // 仅复用定义摘要/字段校验，没有Redis调用。
        return true;
    }
}
