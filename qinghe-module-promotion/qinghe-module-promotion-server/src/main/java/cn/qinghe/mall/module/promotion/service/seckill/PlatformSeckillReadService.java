package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.*;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.promotion.controller.platform.seckill.vo.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.PlatformSeckillReadMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 复用平台身份核对和现有只读RR分页职责；不伪装商家、不设置ignore上下文。 */
@Service
public class PlatformSeckillReadService {
    public static final String QUERY="promotion:seckill-operation:query";
    private final PlatformSeckillReadMapper mapper;
    private final SecurityFrameworkService security;
    private final TransactionTemplate read;
    public PlatformSeckillReadService(PlatformSeckillReadMapper mapper,SecurityFrameworkService security,PlatformTransactionManager manager) {
        this.mapper=mapper;this.security=security;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public PageResult<PlatformSeckillRowRespVO> page(PlatformSeckillPageReqVO query) {
        LoginUser actor=authorize();pageBounds(query);
        if(query.getTenantId()!=null && !id(query.getTenantId()) || query.getStatus()!=null && !Arrays.asList(0,10,20,30,40,50,60).contains(query.getStatus())
                || query.getName()!=null && (query.getName().length()>60 || query.getName().chars().anyMatch(Character::isISOControl)))
            throw exception0(400,"平台活动筛选参数不正确");
        return read.execute(tx->{
            current(actor);Long total=mapper.count(query);count(total);
            int offset=(query.getPageNo()-1)*query.getPageSize();if(offset>=total)return PageResult.empty(total);
            List<PlatformSeckillReadMapper.ActivityRow> rows=mapper.page(query,offset,query.getPageSize());
            if(rows==null || rows.isEmpty() || rows.size()>query.getPageSize())throw incomplete();
            return new PageResult<>(total,rows.stream().map(row->{
                if(row==null || !id(row.getId()) || !id(row.getTenantId()) || query.getTenantId()!=null && !Objects.equals(query.getTenantId(),row.getTenantId())
                        || row.getName()==null || row.getProductName()==null || row.getStatus()==null || !Arrays.asList(0,10,20,30,40,50,60).contains(row.getStatus())
                        || row.getGeneration()==null || row.getGeneration()<0 || row.getVersion()==null || row.getVersion()<0
                        || row.getStartTime()==null || row.getEndTime()==null || !row.getStartTime().isBefore(row.getEndTime()))throw incomplete();
                PlatformSeckillRowRespVO out=new PlatformSeckillRowRespVO();out.setId(row.getId());out.setTenantId(row.getTenantId());out.setName(row.getName());
                out.setProductName(row.getProductName());out.setStatus(row.getStatus());out.setGeneration(row.getGeneration());out.setVersion(row.getVersion());
                out.setStartTime(row.getStartTime().atOffset(ZoneOffset.UTC));out.setEndTime(row.getEndTime().atOffset(ZoneOffset.UTC));return out;
            }).collect(Collectors.toList()));
        });
    }
    public PageResult<PlatformSeckillAuditRespVO> audits(Long tenant,Long activity,PageParam page) {
        LoginUser actor=authorize();pageBounds(page);if(!id(tenant) || !id(activity))throw exception0(400,"需要商家及活动编号");
        return read.execute(tx->{
            current(actor);if(!Long.valueOf(1).equals(mapper.exists(tenant,activity)))throw exception0(404,"目标商家活动不存在");
            Long total=mapper.auditCount(tenant,activity);count(total);int offset=(page.getPageNo()-1)*page.getPageSize();
            if(offset>=total)return PageResult.empty(total);
            List<PlatformSeckillReadMapper.AuditRow> rows=mapper.audits(tenant,activity,offset,page.getPageSize());
            if(rows==null || rows.isEmpty() || rows.size()>page.getPageSize())throw incomplete();
            return new PageResult<>(total,rows.stream().map(row->{
                if(row==null || !id(row.getId()) || row.getActorId()==null || row.getActorId()<0 || row.getActorId()>9007199254740991L
                        || !Arrays.asList("platform","merchant","system").contains(row.getActorRealm())
                        || row.getAction()==null || row.getAction().isEmpty() || row.getFromVersion()==null || row.getFromVersion()<-1
                        || row.getToVersion()==null || row.getToVersion()!=row.getFromVersion()+1 || row.getCreateTime()==null)throw incomplete();
                PlatformSeckillAuditRespVO out=new PlatformSeckillAuditRespVO();out.setId(row.getId());out.setActorId(row.getActorId());out.setActorRealm(row.getActorRealm());out.setAction(row.getAction());
                out.setFromVersion(row.getFromVersion());out.setToVersion(row.getToVersion());out.setCreateTime(row.getCreateTime().atOffset(ZoneOffset.UTC));return out;
            }).collect(Collectors.toList()));
        });
    }
    private LoginUser authorize() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("平台活动查询不能嵌套事务");
        LoginUser actor=SecurityFrameworkUtils.getLoginUser();current(actor);
        if(!security.hasPermission(QUERY))throw exception0(403,"没有平台秒杀运行查询权限");return actor;
    }
    private void current(LoginUser actor) {
        if(actor==null || actor.getExpiresTime()==null || !actor.getExpiresTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))))throw exception0(401,"登录已失效");
        if(!id(actor.getId()) || !UserTypeEnum.ADMIN.getValue().equals(actor.getUserType()) || !Long.valueOf(0).equals(actor.getTenantId())
                || !Long.valueOf(0).equals(TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore()
                || SecurityFrameworkUtils.getLoginUser()!=actor)throw exception0(403,"仅平台身份可查询秒杀运行记录");
    }
    private void pageBounds(PageParam page) {
        if(page==null || page.getPageNo()==null || page.getPageNo()<1 || page.getPageNo()>10000 || page.getPageSize()==null
                || page.getPageSize()<1 || page.getPageSize()>20)throw exception0(400,"每页最多20条，页码最多10000");
    }
    private void count(Long total){if(total==null || total<0 || total>Integer.MAX_VALUE)throw incomplete();}
    private boolean id(Long n){return n!=null && n>0 && n<=9007199254740991L;}
    private cn.qinghe.mall.framework.common.exception.ServiceException incomplete(){return exception0(503,"平台秒杀运行记录不完整，请稍后核对");}
}
