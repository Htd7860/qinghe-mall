package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeOperatingSummaryReqVO;
import cn.qinghe.mall.module.trade.controller.admin.order.vo.TradeOperatingSummaryRespVO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOperatingStatisticsMapper;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOperatingAggregateDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.util.Arrays;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原getOrderSummary聚合职责；当前商家本地账本，不新建统计微服务或异步汇总表。
 * @author owen
 */
@Service
public class TradeOperatingStatisticsService {
    private final TradeOperatingStatisticsMapper records;
    private final Validator validator;
    private final TransactionTemplate read;
    public TradeOperatingStatisticsService(TradeOperatingStatisticsMapper records,Validator validator,PlatformTransactionManager manager) {
        this.records=records;this.validator=validator;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
    }

    @PreAuthorize("@ss.hasPermission('trade:order:query') and @ss.hasPermission('trade:after-sale:query')")
    public TradeOperatingSummaryRespVO summary(TradeOperatingSummaryReqVO request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("经营查询授权不能放在外层事务内");
        LoginUser actor=current();long checkedAt=System.nanoTime();
        if(request==null || !validator.validate(request).isEmpty())throw exception0(400,"统计时间参数不正确");
        if(!request.getBeginTime().isBefore(request.getEndTime())
                || request.getBeginTime().isBefore(LocalDateTime.of(2000,1,1,0,0))
                || request.getEndTime().isAfter(LocalDateTime.of(2100,1,1,0,0))
                || Duration.between(request.getBeginTime(),request.getEndTime()).compareTo(Duration.ofDays(366))>0
                || request.getStatus()!=null && !Arrays.asList(0,10,20,30,40).contains(request.getStatus()))
            throw exception0(400,"请选择有效状态和不超过366天的统计区间");
        return read.execute(tx -> {
            long queriedAt=System.currentTimeMillis();
            long created=records.created(actor.getTenantId(),request);
            TradeOperatingAggregateDTO paid=records.paid(actor.getTenantId(),request);
            TradeOperatingAggregateDTO refunded=records.refunded(actor.getTenantId(),request);
            if(paid==null || refunded==null || paid.getCount()==null || refunded.getCount()==null)
                throw exception0(503,"经营汇总暂不可用");
            TradeOperatingSummaryRespVO result=new TradeOperatingSummaryRespVO();
            result.setTimezone("Asia/Shanghai");result.setBeginTime(epoch(request.getBeginTime()));result.setEndTime(epoch(request.getEndTime()));
            result.setQueriedAt(queriedAt);result.setStatus(request.getStatus());result.setCreatedOrderCount(Long.toString(created));
            result.setPaidOrderCount(paid.getCount().toString());result.setPaidAmount(amount(paid.getAmount()));
            result.setSuccessfulRefundCount(refunded.getCount().toString());result.setRefundGoodsAmount(amount(refunded.getGoodsAmount()));
            result.setRefundDeliveryAmount(amount(refunded.getDeliveryAmount()));result.setRefundAmount(amount(refunded.getAmount()));
            if(refunded.getGoodsAmount().add(refunded.getDeliveryAmount()).compareTo(refunded.getAmount())!=0)
                throw exception0(503,"退款汇总分项与总额不一致");
            result.setPeriodNetAmount(amount(paid.getAmount().subtract(refunded.getAmount())));
            result.setPaidCohortAccumulatedRefundAmount(amount(paid.getRefundAmount()));
            result.setPaidCohortCurrentNetAmount(amount(paid.getAmount().subtract(paid.getRefundAmount())));
            LoginUser fresh=current();
            if(!Objects.equals(fresh.getId(),actor.getId()) || !Objects.equals(fresh.getTenantId(),actor.getTenantId())
                    || System.nanoTime()-checkedAt>5_000_000_000L)throw exception0(503,"身份核对已过时，请重新查询");
            return result;
        });
    }
    private LoginUser current() {
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now()))throw exception0(401,"请重新登录");
        if(!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getTenantId()==null || actor.getTenantId()<=0
                || actor.getTenantId()>9_007_199_254_740_991L || actor.getId()==null || actor.getId()<=0
                || actor.getId()>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId()))throw exception0(403,"经营汇总仅限当前商家员工");
        return actor;
    }
    private String amount(BigDecimal value) {
        if(value==null)throw exception0(503,"经营汇总金额缺失");
        try {return value.toBigIntegerExact().toString();}
        catch(ArithmeticException invalid){throw exception0(503,"经营汇总金额不是整数分");}
    }
    private long epoch(LocalDateTime time){return time.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
}
