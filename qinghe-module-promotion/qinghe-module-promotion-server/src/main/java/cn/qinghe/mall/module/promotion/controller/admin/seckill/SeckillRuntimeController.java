package cn.qinghe.mall.module.promotion.controller.admin.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.promotion.controller.admin.seckill.vo.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRedisEpochDO;
import cn.qinghe.mall.module.promotion.service.seckill.*;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原固定发布意图委托，不收token/目标状态或库存；原发布人、代次、权限由已有Service复查。 */
@RestController @Validated
@RequestMapping("/admin-api/promotion/seckill-activity")
@PreAuthorize("@ss.hasPermission('promotion:seckill-activity:query') && @ss.hasPermission('promotion:seckill-activity:publish')")
public class SeckillRuntimeController {
    private final SeckillPublishDecisionService decisions;
    private final SeckillEpochMaterializationService seeds;
    private final SeckillInitialRuntimeService initialization;
    private final SeckillRuntimeLifecycleService lifecycle;
    private final boolean enabled;
    public SeckillRuntimeController(SeckillPublishDecisionService decisions,SeckillEpochMaterializationService seeds,
            SeckillInitialRuntimeService initialization,SeckillRuntimeLifecycleService lifecycle,
            @Value("${qinghe.promotion.seckill-results.enabled:false}") boolean enabled) {
        this.decisions=decisions;this.seeds=seeds;this.initialization=initialization;this.lifecycle=lifecycle;this.enabled=enabled;
    }
    @ModelAttribute public void prepare(HttpServletRequest request,HttpServletResponse response) {
        SecurityFrameworkUtils.requireBackendRealm(request);response.setHeader("Cache-Control","no-store");
    }
    @GetMapping("/runtime-result")
    public CommonResult<SeckillRuntimeRespVO> result(@Valid SeckillPublishReqDTO intent) {
        SeckillRedisEpochDO row=decisions.getInitialEpoch(intent);
        return CommonResult.success(row==null?null:view(intent,row));
    }
    @PostMapping("/prepare-runtime")
    public CommonResult<SeckillRuntimeRespVO> seed(@Valid @RequestBody SeckillPublishReqDTO intent) {
        requireEnabled();return CommonResult.success(view(intent,seeds.materialize(intent)));
    }
    @PostMapping("/initialize-runtime")
    public CommonResult<SeckillRuntimeRespVO> initialize(@Valid @RequestBody SeckillPublishReqDTO intent) {
        requireEnabled();return CommonResult.success(view(intent,initialization.initialize(intent)));
    }
    @PostMapping("/ready-runtime")
    public CommonResult<SeckillRuntimeRespVO> ready(@Valid @RequestBody SeckillPublishReqDTO intent) {
        requireEnabled();return CommonResult.success(view(intent,lifecycle.ready(intent)));
    }
    @PostMapping("/stop-runtime")
    public CommonResult<SeckillRuntimeRespVO> stop(@Valid @RequestBody SeckillPublishReasonReqVO intent) {
        // 关闭新购买后仍允许处理原停止，不受开放开关阻断。
        return CommonResult.success(view(intent,lifecycle.stop(intent,intent.getReason())));
    }
    private void requireEnabled(){if(!enabled)throw exception0(503,"秒杀运行初始化尚未开放");}
    private SeckillRuntimeRespVO view(SeckillPublishReqDTO intent,SeckillRedisEpochDO row) {
        if(row==null || !Objects.equals(row.getActivityId(),intent.getId()) || !Objects.equals(row.getPublishRequestId(),intent.getRequestId())
                || row.getGeneration()==null || row.getGeneration()<1 || !Long.valueOf(1).equals(row.getEpoch())
                || !Arrays.asList(0,10,15,20,30,40).contains(row.getStatus()) || !Arrays.asList("NOT_REQUIRED","PENDING","SYNCED").contains(row.getStopCacheState())
                || Arrays.asList(15,20,30).contains(row.getStatus()) && row.getInitDeadline()==null)
            throw exception0(503,"原运行阶段结果不完整，请按原发布请求查询");
        SeckillRuntimeRespVO out=new SeckillRuntimeRespVO();out.setId(row.getActivityId());out.setRequestId(row.getPublishRequestId());
        out.setGeneration(row.getGeneration());out.setEpoch(row.getEpoch());out.setStage(row.getStatus());
        out.setInitDeadline(row.getInitDeadline()==null?null:row.getInitDeadline().atOffset(ZoneOffset.UTC));
        out.setCacheCloseState(row.getStopCacheState());out.setServerTime(System.currentTimeMillis());return out;
    }
}
