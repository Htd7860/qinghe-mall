package cn.qinghe.mall.module.trade.service.order;
import cn.qinghe.mall.module.trade.controller.app.order.vo.*;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.qinghe.mall.module.trade.framework.member.TradeMemberAccess;
import cn.qinghe.mall.module.trade.service.order.dto.TradeOrderPrepareResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原createOrder入口职责，直接编排现有准备/创建；不重新实现库存/券/失败补偿。
 * @author 芋道源码
 */
@Service @RequiredArgsConstructor
public class TradeOrderAppService {
    private final TradeOrderPrepareService preparations;
    private final TradeOrderCreationService creation;
    private final TradeOrderPaymentService payments;
    private final TradeOrderQueryService queries;
    private final TradeOrderMapper orders;
    private final TradeMemberAccess access;

    public AppTradeOrderCreateRespVO create(HttpServletRequest request,String store,AppTradeOrderCreateReqVO body) {
        TradeOrderPrepareResult prepared=preparations.prepare(request,store,body);
        if(Arrays.asList("PREPARED","PROCESSING").contains(prepared.getStage()))creation.create(request,store,prepared.getPreparationId());
        LoginUser actor=access.actor();
        TradeOrderDO order=orders.owned(actor.getTenantId(),actor.getId(),prepared.getPreparationId());
        if(order!=null && order.getPayPrice()==0 && order.getStatus()==0 && !Boolean.TRUE.equals(order.getPayStatus()))
            payments.prepare(request,store,order.getId());
        AppTradeOrderCreateRespVO result=queries.creationResultAfterCommand(body.getRequestId());
        if(result==null)throw exception0(503,"创建结果待确认，请查询原请求");
        return result;
    }
    public AppTradeOrderStopRespVO stopUnprepared(HttpServletRequest request,String store,AppTradeOrderCreateReqVO body) {
        boolean stopped=preparations.stopUnprepared(request,store,body);
        AppTradeOrderStopRespVO result=new AppTradeOrderStopRespVO();result.setStopped(stopped);
        result.setResult(stopped?null:queries.creationResultAfterCommand(body.getRequestId()));
        if(!stopped && result.getResult()==null)throw exception0(503,"原受理结果待确认，请查询原请求");
        return result;
    }
}
