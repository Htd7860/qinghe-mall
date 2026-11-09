package cn.qinghe.mall.module.promotion.dal.redis.seckill;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.module.promotion.dal.redis.seckill.dto.SeckillPublishedManifest;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原OAuth2AccessTokenRedisDAO的StringRedisTemplate/JsonUtils访问职责。 @author 芋道源码 */
@Repository
public class SeckillPublishedManifestRedisDAO {
    private static final DefaultRedisScript<Long> INSTALL;
    static {
        INSTALL=new DefaultRedisScript<>();INSTALL.setLocation(new ClassPathResource("lua/seckill-published-manifest.lua"));
        INSTALL.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final String prefix;
    private final Validator validator;

    public SeckillPublishedManifestRedisDAO(StringRedisTemplate redis,Validator validator,
            @Value("${qinghe.redis.key-prefix}") String prefix) {
        if (prefix==null || prefix.length()>128 || !prefix.matches("[A-Za-z0-9:_-]+") || prefix.endsWith(":"))
            throw new IllegalArgumentException("Redis Key前缀配置不合法");
        this.redis=Objects.requireNonNull(redis);this.validator=Objects.requireNonNull(validator);this.prefix=prefix;
    }

    /** 同代次已存在不同定义时拒绝；相同定义不覆盖、不续期。返回摘要不表示准入已就绪。 */
    public String install(SeckillPublishedManifest manifest,Long expiresAt) {
        outsideTransaction();String json=payload(manifest);
        if (expiresAt==null || expiresAt<=0 || expiresAt>manifest.getEndAt()) throw exception0(400,"定义缓存期限不正确");
        Long result=redis.execute(INSTALL,Collections.singletonList(key(manifest)),json,expiresAt.toString(),"INSTALL");
        if (result==null || result!=1L && result!=2L) throw exception0(503,"发布定义缓存未确认，请按原请求重查");
        return DigestUtil.sha256Hex(json);
    }

    /** 只验证不可变定义；miss/Redis异常不能解释为可抢购或可按初始计划重建可用库存。 */
    public boolean matches(SeckillPublishedManifest manifest) {
        outsideTransaction();String expected=payload(manifest);
        Long result=redis.execute(INSTALL,Collections.singletonList(key(manifest)),expected,manifest.getEndAt().toString(),"MATCH");
        if (Long.valueOf(0).equals(result)) return false;
        if (!Long.valueOf(2).equals(result)) throw exception0(503,"原发布定义缓存或期限不一致");
        return true;
    }

    private String payload(SeckillPublishedManifest manifest) {
        if (manifest==null || !validator.validate(manifest).isEmpty() || manifest.getPreparedVersion()!=manifest.getFromVersion()+1
                || manifest.getStartAt()>=manifest.getEndAt() || manifest.getSingleLimit()>manifest.getMemberLimit())
            throw exception0(400,"发布定义字段不完整");
        long previous=0,total=0;
        for (SeckillPublishedManifest.Sku sku:manifest.getSkus()) {
            if (sku==null || sku.getSkuId()<=previous) throw exception0(400,"发布定义规格必须按唯一编号升序");
            previous=sku.getSkuId();total+=sku.getPlannedQuantity();
        }
        String json=JsonUtils.toJsonString(manifest);
        if (total>Integer.MAX_VALUE || json.getBytes(StandardCharsets.UTF_8).length>131072) throw exception0(400,"发布定义超过容量上限");
        return json;
    }
    /** seed永远是不可变暂停快照，不能当运行库存Key；确认过后缺失只查询失败，不能自动补写。 */
    public void installPausedSeed(SeckillRedisEpochDO epoch,Long expiresAt) {
        outsideTransaction();SeckillPublishedManifest manifest=seedDefinition(epoch);
        if (!Integer.valueOf(0).equals(epoch.getStatus()) || expiresAt==null || expiresAt<=0 || expiresAt>manifest.getEndAt())
            throw exception0(409,"只有尚未确认的首次epoch可以写入暂停seed");
        Long result=redis.execute(INSTALL,Collections.singletonList(seedKey(manifest)),seedPayload(epoch,manifest),expiresAt.toString(),"INSTALL");
        if (result==null || result!=1L && result!=2L) throw exception0(503,"暂停seed未确认，请查询原epoch");
    }
    public boolean pausedSeedMatches(SeckillRedisEpochDO epoch) {
        outsideTransaction();SeckillPublishedManifest manifest=seedDefinition(epoch);
        Long result=redis.execute(INSTALL,Collections.singletonList(seedKey(manifest)),seedPayload(epoch,manifest),manifest.getEndAt().toString(),"MATCH");
        if (Long.valueOf(0).equals(result)) return false;
        if (!Long.valueOf(2).equals(result)) throw exception0(503,"暂停seed内容或期限不一致");
        return true;
    }
    private SeckillPublishedManifest seedDefinition(SeckillRedisEpochDO epoch) {
        if (epoch==null || !Long.valueOf(1).equals(epoch.getEpoch())
                || !java.util.Arrays.asList(0,10,15,20,30,40).contains(epoch.getStatus())
                || epoch.getFromVersion()==null || epoch.getToVersion()==null || epoch.getToVersion()!=epoch.getFromVersion()+1
                || epoch.getDefinitionSnapshot()==null) throw exception0(503,"首次epoch凭证不完整");
        SeckillPublishedManifest manifest=JsonUtils.parseObject(epoch.getDefinitionSnapshot(),SeckillPublishedManifest.class);
        String canonical=payload(manifest);
        if (!Objects.equals(manifest.getTenantId(),epoch.getTenantId()) || !Objects.equals(manifest.getActivityId(),epoch.getActivityId())
                || !Objects.equals(manifest.getGeneration(),epoch.getGeneration()) || !Objects.equals(manifest.getPublishRequestId(),epoch.getPublishRequestId())
                || !Objects.equals(DigestUtil.sha256Hex(canonical),epoch.getDefinitionDigest())) throw exception0(503,"首次epoch定义摘要不一致");
        return manifest;
    }
    private String seedPayload(SeckillRedisEpochDO epoch,SeckillPublishedManifest manifest) {
        Map<String,Object> seed=new LinkedHashMap<>();seed.put("schema",1);seed.put("epoch",1);seed.put("phase","PAUSED");
        seed.put("definitionDigest",epoch.getDefinitionDigest());seed.put("definition",manifest);
        // 首次epoch要求从未受理且全部划拨确认；余额只从权威凭证定义派生，不接受客户端覆盖。
        java.util.List<Map<String,Object>> balances=new java.util.ArrayList<>();
        for (SeckillPublishedManifest.Sku sku:manifest.getSkus()) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("skuId",sku.getSkuId());row.put("allocated",sku.getPlannedQuantity());
            row.put("available",sku.getPlannedQuantity());row.put("held",0);row.put("sold",0);row.put("returned",0);balances.add(row);
        }
        seed.put("initialBalances",balances);seed.put("memberBaseline","EMPTY");String json=JsonUtils.toJsonString(seed);
        if (json.getBytes(StandardCharsets.UTF_8).length>131072) throw exception0(503,"暂停seed超过容量上限");
        return json;
    }
    /** 仅供同服务运行初始化核对原seed；不读取/重写Redis，不作为接单证明。 */
    public String expectedPausedSeed(SeckillRedisEpochDO epoch) {
        return seedPayload(epoch,seedDefinition(epoch));
    }
    private String seedKey(SeckillPublishedManifest manifest) {
        return prefix+":seckill:{"+manifest.getTenantId()+":"+manifest.getActivityId()+":"+manifest.getGeneration()+"}:epoch:1:seed";
    }
    private String key(SeckillPublishedManifest manifest) {
        return prefix+":seckill:{"+manifest.getTenantId()+":"+manifest.getActivityId()+":"+manifest.getGeneration()+"}:manifest";
    }
    private void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("定义缓存不能在数据库事务内读写");
    }
}
