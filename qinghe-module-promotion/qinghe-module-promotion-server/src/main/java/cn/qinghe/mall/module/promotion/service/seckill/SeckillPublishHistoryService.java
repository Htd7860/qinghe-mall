package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.*;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillPublishHistoryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 沿用已有后台分页/权限模式；历史查询不执行库存、活动状态或恢复命令。 */
@Service
public class SeckillPublishHistoryService {
    private final SeckillPublishHistoryMapper history;
    private final SeckillActivityMapper activities;
    private final SecurityFrameworkService security;
    private final TransactionTemplate read;

    public SeckillPublishHistoryService(SeckillPublishHistoryMapper history,SeckillActivityMapper activities,
            SecurityFrameworkService security,PlatformTransactionManager manager) {
        this.history=history;this.activities=activities;this.security=security;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public PageResult<SeckillPublishHistoryDTO> page(Long activityId,PageParam page) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("历史查询不能嵌套外层事务");
        if (activityId==null || activityId<=0 || activityId>9_007_199_254_740_991L || page==null || page.getPageNo()==null
                || page.getPageNo()<1 || page.getPageNo()>10000 || page.getPageSize()==null || page.getPageSize()<1 || page.getPageSize()>20)
            throw exception0(400,"活动历史分页参数不正确");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();
        if (actor==null) throw exception0(401,"请先登录");
        if (!UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || actor.getId()==null || actor.getId()<=0 || actor.getId()>9_007_199_254_740_991L
                || actor.getTenantId()==null || actor.getTenantId()<=0
                || actor.getTenantId()>9_007_199_254_740_991L || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(),TenantContextHolder.getTenantId())) throw exception0(403,"活动历史仅限当前商家");
        if (!security.hasPermission("promotion:seckill-activity:query")) throw exception0(403,"没有活动查询权限");
        int offset=(page.getPageNo()-1)*page.getPageSize();
        return read.execute(tx -> {
            if (actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))
                throw exception0(401,"活动查询身份已到期");
            if (activities.get(actor.getTenantId(),activityId)==null) throw exception0(404,"活动不存在");
            Long total=history.count(actor.getTenantId(),activityId);
            if (total==null || total<0 || total>Integer.MAX_VALUE) throw exception0(503,"原活动历史数量异常");
            if (offset>=total) return PageResult.empty(total);
            List<SeckillPublishHistoryDTO> rows=history.page(actor.getTenantId(),activityId,offset,page.getPageSize());
            if (rows==null || rows.isEmpty() || rows.size()>page.getPageSize()) throw exception0(503,"原发布历史不完整");
            for (SeckillPublishHistoryDTO row:rows) {
                if (row.getRequestId()==null || !row.getRequestId().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                        || row.getActorId()==null || row.getActorId()<=0 || row.getActorId()>9_007_199_254_740_991L
                        || row.getFromVersion()==null || row.getFromVersion()<0 || row.getFromVersion()>=9_007_199_254_740_991L
                        || row.getPreparedVersion()==null || row.getPreparedVersion()!=row.getFromVersion()+1 || row.getGeneration()==null || row.getGeneration()<1
                        || row.getPreparedAt()==null || row.getPublished()==null || row.getWithdrawn()==null || row.getSettled()==null
                        || row.getWithdrawn() && !row.getPublished()) throw exception0(503,"原发布历史字段不完整");
            }
            return new PageResult<>(total,rows);
        });
    }
}
