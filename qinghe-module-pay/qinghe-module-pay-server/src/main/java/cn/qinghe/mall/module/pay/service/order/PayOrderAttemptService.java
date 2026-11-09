package cn.qinghe.mall.module.pay.service.order;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.pay.dal.dataobject.app.PayAppDO;
import cn.qinghe.mall.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.qinghe.mall.module.pay.dal.dataobject.order.*;
import cn.qinghe.mall.module.pay.dal.mysql.app.PayAppMapper;
import cn.qinghe.mall.module.pay.dal.mysql.channel.PayChannelMapper;
import cn.qinghe.mall.module.pay.dal.mysql.order.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClient;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;
import cn.qinghe.mall.module.pay.service.channel.PayChannelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原submitOrder先保存extension再调用client的职责；一笔账只保留一个稳定渠道尝试。
 * 仅供可信内部服务命令调用；调用方须核实订单/当前会员及付款资格，不能信浏览器userId。
 * 关单先持久停止提交，再渠道确认；超时保留原状态，不能释放交易资源。
 * @author 芋道源码
 */
@Service
public class PayOrderAttemptService {
    private final PayOrderMapper orders;
    private final PayOrderExtensionMapper attempts;
    private final PayChannelMapper channels;
    private final PayAppMapper apps;
    private final PayChannelService clients;
    private final TransactionTemplate write;
    public PayOrderAttemptService(PayOrderMapper orders,PayOrderExtensionMapper attempts,PayChannelMapper channels,
            PayAppMapper apps,PayChannelService clients,PlatformTransactionManager manager) {
        this.orders=orders;this.attempts=attempts;this.channels=channels;this.apps=apps;this.clients=clients;
        write=new TransactionTemplate(manager);write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);write.setTimeout(10);
    }

    public PayOrderExtensionDO submit(Long orderId,Long trustedUserId,Long channelId) {
        Long tenant=context(orderId,trustedUserId);positive(channelId);
        PayChannelDO configured=channels.get(tenant,channelId);
        if(configured==null)throw exception0(404,"本店支付渠道不存在");
        // 凭据/SDK初始化在登记尝试之前，缺配置不会留下一个虚假的已提交记录。
        PayClient<AlipayPayClientConfig> client=clients.client(configured,configured.getAuthVersion());
        Submission saved=write.execute(status -> {
            PayOrderDO order=owned(orders.lockId(tenant,orderId),trustedUserId);
            PayOrderExtensionDO old=attempts.find(tenant,orderId);
            if(old!=null) {
                if(!Objects.equals(old.getChannelId(),channelId))throw exception0(409,"原支付尝试不能切换渠道");
                if(Boolean.TRUE.equals(order.getClosing()))throw exception0(409,"原支付单正在关闭");
                return new Submission(order,old,false);
            }
            if(order.getStatus()!=0 || Boolean.TRUE.equals(order.getClosing()) || !now().isBefore(order.getExpireTime()))
                throw exception0(409,"原支付单不在有效待支付阶段");
            PayAppDO app=apps.find(tenant,"qinghe-trade");
            PayChannelDO channel=channels.lock(tenant,channelId);
            if(app==null || app.getStatus()!=0 || !Objects.equals(app.getId(),order.getAppId())
                    || channel==null || channel.getStatus()!=0 || !Objects.equals(order.getAppId(),channel.getAppId())
                    || !configured.equals(channel))throw exception0(409,"支付应用或渠道配置已变化");
            PayOrderExtensionDO row=new PayOrderExtensionDO();row.setTenantId(tenant);row.setOrderId(orderId);
            row.setChannelId(channelId);row.setChannelVersion(channel.getAuthVersion());
            row.setNo("P"+UUID.randomUUID().toString().replace("-",""));row.setState("SUBMITTING");row.setAuthVersion(0L);
            row.setCreateTime(now());row.setUpdateTime(row.getCreateTime());
            if(attempts.insert(row)!=1 || row.getId()==null)throw new IllegalStateException("支付尝试保存失败");
            return new Submission(order,row,true);
        });
        if(saved==null)throw new IllegalStateException("支付尝试登记无结果");
        if(!saved.fresh) {
            if(Arrays.asList("WAITING","SUCCESS","CLOSED","REVIEW").contains(saved.attempt.getState()))return saved.attempt;
            // 不生成新流水或盲目再发起；没有查到成功仍视为在途，后续按原号恢复。
            return query(orderId,trustedUserId);
        }
        PayOrderUnifiedReqDTO request=new PayOrderUnifiedReqDTO();
        request.setOutTradeNo(saved.attempt.getNo());request.setSubject(saved.order.getSubject());
        request.setPrice(saved.order.getPrice());request.setExpireTime(saved.order.getExpireTime());request.setNotifyUrl(configured.getNotifyUrl());
        PayOrderRespDTO result=client.unifiedOrder(request);
        return record(tenant,saved.order,saved.attempt,result);
    }

    public PayOrderExtensionDO query(Long orderId,Long trustedUserId) {
        Long tenant=context(orderId,trustedUserId);
        PayOrderDO order=owned(orders.get(tenant,orderId),trustedUserId);
        PayOrderExtensionDO attempt=attempts.find(tenant,orderId);
        if(attempt==null)return null;
        if(Arrays.asList("SUCCESS","CLOSED","REVIEW").contains(attempt.getState()))return attempt;
        PayChannelDO channel=channel(tenant,order,attempt);
        PayOrderRespDTO result=clients.client(channel,attempt.getChannelVersion()).getOrder(attempt.getNo());
        return record(tenant,order,attempt,result);
    }

    /** 显式恢复原扫码请求；父账锁内领取一次有界重取，SDK在锁外，永不创建新attempt/no。 */
    public PayOrderExtensionDO recoverQr(Long orderId,Long trustedUserId) {
        Long tenant=context(orderId,trustedUserId);
        PayOrderDO original=owned(orders.get(tenant,orderId),trustedUserId);
        PayOrderExtensionDO previous=attempts.find(tenant,orderId);
        if(previous==null)throw exception0(409,"原付款尝试尚未查到，请先查询付款结果");
        if(original.getStatus()!=0 || Boolean.TRUE.equals(original.getClosing())
                || !Arrays.asList("SUBMITTING","WAITING").contains(previous.getState()) || hasQr(previous))return previous;
        PayChannelDO configured=channel(tenant,original,previous);
        PayClient<AlipayPayClientConfig> client=clients.client(configured,previous.getChannelVersion());
        Submission saved=write.execute(status -> {
            PayOrderDO order=owned(orders.lockId(tenant,orderId),trustedUserId);
            PayOrderExtensionDO row=attempts.find(tenant,orderId);
            if(row==null || !Objects.equals(previous.getId(),row.getId()) || !Objects.equals(previous.getNo(),row.getNo())
                    || !Objects.equals(previous.getChannelId(),row.getChannelId()) || !Objects.equals(previous.getChannelVersion(),row.getChannelVersion()))
                throw exception0(409,"原付款尝试或渠道身份已变化");
            if(order.getStatus()!=0 || Boolean.TRUE.equals(order.getClosing()) || !Arrays.asList("SUBMITTING","WAITING").contains(row.getState()) || hasQr(row))
                return new Submission(order,row,false);
            LocalDateTime instant=now();
            if(!instant.isBefore(order.getExpireTime()))throw exception0(409,"原订单付款期限已到，不能重取二维码");
            PayAppDO app=apps.find(tenant,"qinghe-trade");PayChannelDO channel=channels.lock(tenant,row.getChannelId());
            if(app==null || app.getStatus()!=0 || !Objects.equals(app.getId(),order.getAppId()) || channel==null
                    || channel.getStatus()!=0 || !configured.equals(channel))throw exception0(409,"原支付应用或渠道已停用或变化");
            if(row.getQrRecoveryCount()==null || row.getQrRecoveryCount()<0 || row.getQrRecoveryCount()>=3)
                throw exception0(409,"原付款二维码恢复次数已用完，请继续查询付款结果");
            if(instant.isBefore(row.getCreateTime().plusSeconds(30)) || row.getQrRecoveryAfter()!=null && instant.isBefore(row.getQrRecoveryAfter()))
                throw exception0(409,"原付款仍在处理中，请稍后再恢复");
            LocalDateTime after=instant.plusSeconds(30);
            changed(attempts.claimQrRecovery(tenant,row.getId(),row.getAuthVersion(),instant,after));
            row.setQrRecoveryCount(row.getQrRecoveryCount()+1);row.setQrRecoveryAfter(after);
            return new Submission(order,row,true);
        });
        if(saved==null)throw new IllegalStateException("原二维码恢复领取无结果");
        if(!saved.fresh)return saved.attempt;
        PayOrderUnifiedReqDTO request=new PayOrderUnifiedReqDTO();request.setOutTradeNo(saved.attempt.getNo());
        request.setSubject(saved.order.getSubject());request.setPrice(saved.order.getPrice());
        request.setExpireTime(saved.order.getExpireTime());request.setNotifyUrl(configured.getNotifyUrl());
        return record(tenant,saved.order,saved.attempt,client.recoverQr(request));
    }
    private boolean hasQr(PayOrderExtensionDO row){return row.getDisplayContent()!=null && !row.getDisplayContent().isEmpty();}

    /** 原notifyOrder职责；路由参数只定位配置，只有验签通过的原流水才参与入账。 */
    public void notifyOrder(Long channelId,Map<String,String> parameters) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("通知验签必须在数据库事务之外");
        positive(channelId);
        PayChannelDO channel=TenantUtils.executeIgnore(() -> channels.locateNotifyChannel(channelId));
        if(channel==null || channel.getTenantId()==null || channel.getTenantId()<=0)throw exception0(404,"支付渠道不存在");
        TenantUtils.execute(channel.getTenantId(),() -> {
            PayOrderRespDTO result=clients.client(channel,channel.getAuthVersion()).parseOrderNotify(parameters,null,Collections.emptyMap());
            PayOrderExtensionDO attempt=attempts.findNo(channel.getTenantId(),result.getOutTradeNo());
            if(attempt==null || !Objects.equals(channelId,attempt.getChannelId())
                    || !Objects.equals(channel.getAuthVersion(),attempt.getChannelVersion()))throw exception0(409,"原支付尝试或渠道版本不匹配");
            PayOrderDO order=orders.get(channel.getTenantId(),attempt.getOrderId());
            if(order==null || !Objects.equals(order.getAppId(),channel.getAppId()))throw exception0(409,"原支付应用不匹配");
            record(channel.getTenantId(),order,attempt,result);
            return null;
        });
    }

    /** 返回主支付账状态：仅30确认未付关闭，10/20已有付款，0仍在关闭；不根据期限推测。 */
    public Integer close(Long orderId,Long trustedUserId) {
        Long tenant=context(orderId,trustedUserId);
        PayOrderDO original=write.execute(status -> {
            PayOrderDO row=owned(orders.lockId(tenant,orderId),trustedUserId);
            if(row.getStatus()!=0)return row;
            if(!Boolean.TRUE.equals(row.getClosing())) {
                changed(orders.beginClose(tenant,orderId,row.getAuthVersion(),now()));
                row.setClosing(true);row.setAuthVersion(Math.addExact(row.getAuthVersion(),1));
            }
            PayOrderExtensionDO attempt=attempts.find(tenant,orderId);
            if(attempt==null) {
                changed(orders.finishClose(tenant,orderId,row.getAuthVersion(),now()));row.setStatus(30);return row;
            }
            if(!Arrays.asList("CLOSING","CLOSED","SUCCESS","REVIEW").contains(attempt.getState()))
                changed(attempts.record(tenant,attempt.getId(),attempt.getAuthVersion(),"CLOSING",null,null,null,now()));
            return row;
        });
        if(original==null)throw new IllegalStateException("支付关闭未保存结果");
        if(original.getStatus()!=0)return original.getStatus();
        PayOrderExtensionDO attempt=attempts.find(tenant,orderId);
        if(attempt==null)throw exception0(503,"原支付尝试待确认");
        PayChannelDO channel=channel(tenant,original,attempt);
        PayOrderRespDTO closed=clients.client(channel,attempt.getChannelVersion()).closeOrder(attempt.getNo());
        if(closed==null || closed.getStatus()==null || closed.getStatus()!=30 || !attempt.getNo().equals(closed.getOutTradeNo()))
            throw exception0(503,"渠道尚未确认关闭");
        return write.execute(status -> {
            PayOrderDO row=owned(orders.lockId(tenant,orderId),trustedUserId);
            if(row.getStatus()!=0)return row.getStatus();
            PayOrderExtensionDO locked=attempts.find(tenant,orderId);
            if(locked==null || !Boolean.TRUE.equals(row.getClosing()) || "REVIEW".equals(locked.getState()))throw exception0(503,"关闭结果需要核查");
            if("SUCCESS".equals(locked.getState()))throw exception0(503,"原尝试已有付款结果，禁止按未付关闭");
            changed(attempts.record(tenant,locked.getId(),locked.getAuthVersion(),"CLOSED",null,null,null,now()));
            changed(orders.finishClose(tenant,orderId,row.getAuthVersion(),now()));return 30;
        });
    }

    private PayOrderExtensionDO record(Long tenant,PayOrderDO original,PayOrderExtensionDO attempt,PayOrderRespDTO result) {
        if(result==null || !attempt.getNo().equals(result.getOutTradeNo()) || !Objects.equals(original.getPrice(),result.getPrice())
                || result.getStatus()==null || !Arrays.asList(0,10).contains(result.getStatus()))throw exception0(503,"渠道结果与原支付尝试不一致");
        if(result.getStatus()==10 && (result.getSuccessTime()==null || result.getSuccessTime().isBefore(original.getCreateTime().minusSeconds(1))
                || result.getSuccessTime().isAfter(now().plusMinutes(1)) || result.getChannelOrderNo()==null
                || !result.getChannelOrderNo().matches("[A-Za-z0-9_-]{1,128}")))throw exception0(503,"渠道付款凭据不完整");
        if(result.getStatus()==0 && result.getDisplayContent()!=null && result.getDisplayContent().length()>4096)
            throw exception0(503,"渠道展示内容超出范围");
        return write.execute(status -> {
            PayOrderDO order=owned(orders.lockId(tenant,original.getId()),original.getUserId());
            PayOrderExtensionDO row=attempts.find(tenant,original.getId());
            if(row==null || !Objects.equals(row.getId(),attempt.getId()))throw exception0(503,"原支付尝试不存在");
            if(result.getStatus()==10) {
                if(row.getChannelOrderNo()!=null && !row.getChannelOrderNo().equals(result.getChannelOrderNo()))throw exception0(409,"渠道流水号不一致");
                if("SUCCESS".equals(row.getState()) || "REVIEW".equals(row.getState()))return row;
                boolean review=order.getStatus()==30 || !result.getSuccessTime().isBefore(order.getExpireTime());
                String state=review?"REVIEW":"SUCCESS";
                if(order.getStatus()==0 && !review)changed(orders.paid(tenant,order.getId(),order.getAuthVersion(),result.getSuccessTime(),now()));
                if(order.getStatus()==0 && review && !Boolean.TRUE.equals(order.getClosing()))
                    changed(orders.beginClose(tenant,order.getId(),order.getAuthVersion(),now()));
                changed(attempts.record(tenant,row.getId(),row.getAuthVersion(),state,null,result.getChannelOrderNo(),result.getSuccessTime(),now()));
                row.setState(state);row.setChannelOrderNo(result.getChannelOrderNo());row.setSuccessTime(result.getSuccessTime());
            }else if(order.getStatus()==0 && !Boolean.TRUE.equals(order.getClosing()) && Arrays.asList("SUBMITTING","WAITING").contains(row.getState())) {
                // 首个已持久二维码优先，晚到的同号恢复不能替换已展示的码。
                String display=hasQr(row)?null:result.getDisplayContent();
                changed(attempts.record(tenant,row.getId(),row.getAuthVersion(),"WAITING",display,null,null,now()));
                row.setState("WAITING");if(display!=null)row.setDisplayContent(display);
            }
            return row;
        });
    }
    private PayChannelDO channel(Long tenant,PayOrderDO order,PayOrderExtensionDO attempt) {
        PayChannelDO channel=channels.get(tenant,attempt.getChannelId());
        if(channel==null || !Objects.equals(order.getAppId(),channel.getAppId())
                || !Objects.equals(channel.getAuthVersion(),attempt.getChannelVersion()))throw exception0(503,"原渠道版本缺失，不能切换其他渠道查询");
        return channel;
    }
    private PayOrderDO owned(PayOrderDO order,Long user){if(order==null || !Objects.equals(order.getUserId(),user))throw exception0(404,"本人支付单不存在");return order;}
    private Long context(Long order,Long user) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("支付尝试流程不能置于外层事务内");
        positive(order);positive(user);Long tenant=TenantContextHolder.getTenantId();
        if(tenant==null || tenant<=0 || tenant>9_007_199_254_740_991L || TenantContextHolder.isIgnore())throw exception0(403,"支付尝试需要可信租户");return tenant;
    }
    private void positive(Long id){if(id==null || id<=0 || id>9_007_199_254_740_991L)throw exception0(400,"支付身份编号不正确");}
    private void changed(int count){if(count!=1)throw exception0(409,"支付阶段或版本已变化，请查询原结果");}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
    private static class Submission {
        final PayOrderDO order;final PayOrderExtensionDO attempt;final boolean fresh;
        Submission(PayOrderDO order,PayOrderExtensionDO attempt,boolean fresh){this.order=order;this.attempt=attempt;this.fresh=fresh;}
    }
}
