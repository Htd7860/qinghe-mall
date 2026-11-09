package cn.qinghe.mall.module.promotion.framework.product;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillPublishPreparationService;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillPoolReturnDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillPoolReturnMapper;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillHistoricalRefundMapper;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillClearingPoolDO;
import cn.qinghe.mall.module.promotion.service.seckill.SeckillRefundCommandContext;
import cn.qinghe.mall.module.promotion.service.seckill.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.product.api.seckill.dto.ProductSeckillAllocationRespDTO.*;

/**
 * 仅接原发布意图+SKU，从持久冻结快照读取全部库存参数；禁止本地事务内RPC/隐式补偿。
 * 不是发布协调器：不更新promotion池、不标已发布、不自动批量调用或重试。
 */
@Service
@EnableConfigurationProperties(SeckillStockProperties.class)
public class SeckillStockClient {
    private final SeckillStockApi api;
    private final SeckillStockProperties settings;
    private final SeckillPublishPreparationService preparations;
    private final SeckillPoolReturnMapper returns;
    private final SeckillHistoricalRefundMapper history;
    private final Semaphore calls=new Semaphore(8);

    public SeckillStockClient(SeckillStockApi api,SeckillStockProperties settings,SeckillPublishPreparationService preparations,SeckillPoolReturnMapper returns,SeckillHistoricalRefundMapper history,
            @Value("${qinghe.promotion.product-base-url:http://127.0.0.1:8082}") String source) {
        URI uri=URI.create(source);
        if (uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && Arrays.asList("127.0.0.1","localhost","::1","[::1]").contains(uri.getHost())))
            throw new IllegalArgumentException("秒杀库存来源必须为固定可信origin");
        if (settings.getServiceToken()==null || !settings.getServiceToken().isEmpty() && !settings.getServiceToken().matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("秒杀库存服务凭据格式不正确");
        this.api=api;this.settings=settings;this.preparations=preparations;this.returns=returns;this.history=history;
    }

    public ProductSeckillAllocationRespDTO allocate(SeckillPublishReqDTO intent,Long skuId) {
        SeckillPublishPlanDTO plan=plan(intent);
        if (!Integer.valueOf(10).equals(plan.getCurrentStatus()) || !Objects.equals(plan.getCurrentVersion(),plan.getPreparedVersion()))
            throw exception0(409,"活动已退出原发布准备，不能继续划拨");
        ProductSeckillAllocationReqDTO request=request(plan,skuId);
        ProductSeckillAllocationRespDTO result=call(() -> api.allocate(bearer(),tenant(),request),request,false);
        if (result.getStatus()!=ALLOCATED && result.getStatus()!=COMPENSATED) throw incomplete();
        return result;
    }

    /** 只查询原记录；null不触发补偿或换号重试。 */
    public ProductSeckillAllocationRespDTO result(SeckillPublishReqDTO intent,Long skuId) {
        SeckillPublishPlanDTO plan=plan(intent);ProductSeckillAllocationReqDTO request=request(plan,skuId);
        return call(() -> api.result(bearer(),tenant(),request),request,true);
    }

    /** 协调器先持久失败准备或已确认开卖前撤回为60；不能直接补偿准备10或仍发布20。 */
    public ProductSeckillAllocationRespDTO compensate(SeckillPublishReqDTO intent,Long skuId) {
        SeckillPublishPlanDTO plan=plan(intent);
        if (!Integer.valueOf(60).equals(plan.getCurrentStatus())) throw exception0(409,"请先持久停止本代发布并确认未开放准入");
        ProductSeckillAllocationReqDTO request=request(plan,skuId);
        ProductSeckillAllocationRespDTO result=call(() -> api.compensate(bearer(),tenant(),request),request,false);
        if (result.getStatus()!=COMPENSATED) throw incomplete();return result;
    }

    /** 只接受本库原归还号，原划拨元组和累计数量必须从已持久预留读取。 */
    public ProductSeckillAllocationRespDTO returnResult(String returnNo) {
        SeckillPoolReturnDO plan=returnPlan(returnNo);ProductSeckillAllocationReqDTO original=returnRequest(plan);
        return call(()->api.result(bearer(),tenant(),original),original,true);
    }
    public ProductSeckillAllocationRespDTO returnStock(String returnNo) {
        SeckillPoolReturnDO plan=returnPlan(returnNo);
        if(!"PENDING".equals(plan.getState()))throw exception0(409,"原归还已确认，不能重新提交");
        ProductSeckillAllocationReqDTO original=returnRequest(plan);
        ProductSeckillReturnReqDTO body=new ProductSeckillReturnReqDTO();body.setAllocation(original);body.setReturnedQuantity(plan.getTargetReturned());
        return call(()->api.returnStock(bearer(),tenant(),body),original,false);
    }
    public ProductSeckillAllocationRespDTO historicalRefundResult(Long afterSaleId) {
        ProductSeckillAllocationReqDTO original=returnRequest(historicalPlan(afterSaleId));
        return call(()->api.result(bearer(),tenant(),original),original,true);
    }
    public ProductSeckillAllocationRespDTO returnHistoricalRefund(Long afterSaleId) {
        SeckillPoolReturnDO plan=historicalPlan(afterSaleId);
        if(!"PENDING".equals(plan.getState()))throw exception0(409,"原历史归还已确认，请查询原售后结果");
        ProductSeckillAllocationReqDTO original=returnRequest(plan);
        ProductSeckillReturnReqDTO body=new ProductSeckillReturnReqDTO();body.setAllocation(original);body.setReturnedQuantity(plan.getTargetReturned());
        return call(()->api.returnStock(bearer(),tenant(),body),original,false);
    }
    private SeckillPoolReturnDO historicalPlan(Long afterSaleId) {
        SeckillRefundCommandContext.requireCaller();
        if(afterSaleId==null || afterSaleId<=0 || afterSaleId>9007199254740991L)throw exception0(400,"需要原售后号");
        Long tenant=tenant();SeckillPoolReturnDO plan=history.plan(tenant,afterSaleId);
        if(plan==null)throw exception0(409,"原历史归还计划尚未确认");
        SeckillClearingPoolDO baseline=history.baseline(tenant,plan.getActivityId(),plan.getGeneration(),plan.getSkuId());
        if(baseline==null || !Objects.equals(plan.getAllocationOperationNo(),baseline.getAllocationOperationNo())
                || !Objects.equals(plan.getAllocationSnapshot(),baseline.getAllocationSnapshot()) || !Objects.equals(plan.getRedisEpoch(),baseline.getRedisEpoch()))
            throw exception0(409,"原历史归还与实际清算基线不一致");
        return plan;
    }
    private SeckillPoolReturnDO returnPlan(String returnNo) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || RequestContextHolder.getRequestAttributes()!=null
                || SecurityContextHolder.getContext().getAuthentication()!=null)throw new IllegalStateException("原池归还RPC仅限事务外可信后台");
        if(returnNo==null || !returnNo.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw exception0(400,"需要原归还号");
        SeckillPoolReturnDO plan=returns.get(tenant(),returnNo);
        if(plan==null || !"POOL".equals(plan.getSourceKind()))throw exception0(409,"原池归还预留尚未持久确认或来源不匹配");return plan;
    }
    private ProductSeckillAllocationReqDTO returnRequest(SeckillPoolReturnDO plan) {
        ProductSeckillAllocationReqDTO original=JsonUtils.parseObject(plan.getAllocationSnapshot(),ProductSeckillAllocationReqDTO.class);
        if(original==null || !Objects.equals(original.getOperationNo(),plan.getAllocationOperationNo()) || !Objects.equals(original.getGeneration(),plan.getGeneration())
                || !Objects.equals(original.getSkuId(),plan.getSkuId()) || plan.getQuantity()==null || plan.getQuantity()<1
                || plan.getBaseReturned()==null || plan.getBaseReturned()<0 || plan.getTargetReturned()==null
                || (long)plan.getBaseReturned()+plan.getQuantity()!=plan.getTargetReturned() || original.getQuantity()==null || plan.getTargetReturned()>original.getQuantity())
            throw exception0(503,"原归还累计目标或划拨快照不完整");
        return original;
    }

    private SeckillPublishPlanDTO plan(SeckillPublishReqDTO intent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("秒杀库存RPC不能在本地事务内执行");
        SeckillPublishPlanDTO plan=preparations.get(intent);
        if (plan==null) throw exception0(404,"原发布准备尚未确认，请先查询原请求");
        tenant();return plan;
    }
    private ProductSeckillAllocationReqDTO request(SeckillPublishPlanDTO plan,Long sku) {
        return plan.getAllocations().stream().filter(row->Objects.equals(row.getSkuId(),sku)).findFirst()
                .orElseThrow(()->exception0(404,"原发布计划没有该规格"));
    }
    private ProductSeckillAllocationRespDTO call(Supplier<CommonResult<ProductSeckillAllocationRespDTO>> action,
            ProductSeckillAllocationReqDTO original,boolean nullable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("库存RPC必须在事务外");
        if (!calls.tryAcquire()) throw exception0(503,"秒杀库存服务繁忙，请查询原结果");
        try {
            CommonResult<ProductSeckillAllocationRespDTO> response;
            try { response=action.get(); }
            catch (cn.qinghe.mall.framework.common.exception.ServiceException known) { throw known; }
            catch (RuntimeException unknown) { throw exception0(503,"秒杀库存结果待确认，请按原发布号查询"); }
            if (response==null || response.getCode()==null) throw incomplete();
            response.checkError();ProductSeckillAllocationRespDTO result=response.getData();
            if (nullable && result==null) return null;
            if (result==null || !Objects.equals(result.getActivityNo(),original.getActivityNo())
                    || !Objects.equals(result.getGeneration(),original.getGeneration()) || !Objects.equals(result.getOperationNo(),original.getOperationNo())
                    || !Objects.equals(result.getSpuId(),original.getSpuId()) || !Objects.equals(result.getSkuId(),original.getSkuId())
                    || !Objects.equals(result.getSpuVersion(),original.getSpuVersion()) || !Objects.equals(result.getSkuVersion(),original.getSkuVersion())
                    || !Objects.equals(result.getQuantity(),original.getQuantity()) || result.getStatus()==null
                    || !Arrays.asList(ALLOCATED,COMPENSATED,RETURNED,REVIEW,RETURNING).contains(result.getStatus())
                    || result.getVersion()==null || result.getVersion()<1 || result.getVersion()>9_007_199_254_740_991L
                    || result.getAllocatedQuantity()==null || (result.getAllocatedQuantity()!=0 && !result.getAllocatedQuantity().equals(result.getQuantity()))
                    || result.getReturnedQuantity()==null || result.getReturnedQuantity()<0 || result.getReturnedQuantity()>result.getAllocatedQuantity()
                    || (result.getStatus()==ALLOCATED && (!result.getAllocatedQuantity().equals(result.getQuantity()) || result.getReturnedQuantity()!=0))
                    || (result.getStatus()==COMPENSATED && !result.getReturnedQuantity().equals(result.getAllocatedQuantity()))
                    || (result.getStatus()==RETURNED && (!result.getAllocatedQuantity().equals(result.getQuantity()) || !result.getReturnedQuantity().equals(result.getAllocatedQuantity())))
                    || (result.getStatus()==RETURNING && (!result.getAllocatedQuantity().equals(result.getQuantity()) || result.getReturnedQuantity()<=0 || result.getReturnedQuantity()>=result.getAllocatedQuantity()))) throw incomplete();
            return result;
        } finally { calls.release(); }
    }
    private String bearer() {
        if (settings.getServiceToken()==null || !settings.getServiceToken().matches("[A-Za-z0-9_-]{43,128}"))
            throw exception0(503,"秒杀库存服务凭据未配置");
        return "Bearer "+settings.getServiceToken();
    }
    private Long tenant() {
        Long tenant=TenantContextHolder.getTenantId();
        if (tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore()) throw exception0(403,"秒杀库存需要可信当前租户");
        return tenant;
    }
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原发布库存结果不完整，请查询后核对"); }
}
