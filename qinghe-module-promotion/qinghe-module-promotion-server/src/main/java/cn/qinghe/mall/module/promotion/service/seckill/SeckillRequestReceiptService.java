package cn.qinghe.mall.module.promotion.service.seckill;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.common.pojo.PageParam;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.promotion.framework.member.CouponMemberAccess;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.SeckillRequestDO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillAdmissionMapper;
import cn.qinghe.mall.module.promotion.service.seckill.dto.SeckillRequestReceiptDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import javax.servlet.http.HttpServletRequest;
import java.time.ZoneOffset;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 沿用本人券/订单的请求恢复方式；仅查询，不接受申请、不推进状态、不调用Redis或MQ。 */
@Service
public class SeckillRequestReceiptService {
    private final CouponMemberAccess access;
    private final SeckillAdmissionMapper requests;
    private final TransactionTemplate read;
    public SeckillRequestReceiptService(CouponMemberAccess access,SeckillAdmissionMapper requests,PlatformTransactionManager manager) {
        this.access=access;this.requests=requests;
        read=new TransactionTemplate(manager);read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setReadOnly(true);read.setTimeout(5);
    }
    public SeckillRequestReceiptDTO byClient(HttpServletRequest request,String store,String clientRequestId) {
        return lookup(request,store,clientRequestId,true);
    }
    public SeckillRequestReceiptDTO byRequest(HttpServletRequest request,String store,String requestNo) {
        return lookup(request,store,requestNo,false);
    }
    public PageResult<SeckillRequestReceiptDTO> page(HttpServletRequest request,String store,PageParam query) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("本人申请分页不能嵌套外事务");
        if(query==null || query.getPageNo()==null || query.getPageNo()<1 || query.getPageNo()>10000
                || query.getPageSize()==null || query.getPageSize()<1 || query.getPageSize()>20)throw exception0(400,"每页最多20条本人申请");
        access.check(request,store);LoginUser actor=access.actor();
        return read.execute(tx->{
            LoginUser current=access.actor();
            if(!Objects.equals(actor.getTenantId(),current.getTenantId()) || !Objects.equals(actor.getId(),current.getId()))throw exception0(401,"查询身份已变化");
            Long total=requests.ownCount(actor.getTenantId(),actor.getId());
            if(total==null || total<0 || total>Integer.MAX_VALUE)throw incomplete();
            int offset=(query.getPageNo()-1)*query.getPageSize();if(offset>=total)return PageResult.empty(total);
            List<SeckillRequestDO> rows=requests.ownPage(actor.getTenantId(),actor.getId(),offset,query.getPageSize());
            if(rows==null || rows.isEmpty() || rows.size()>query.getPageSize())throw incomplete();
            List<SeckillRequestReceiptDTO> result=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(SeckillRequestDO row:rows) {
                if(row==null || !Objects.equals(actor.getTenantId(),row.getTenantId()) || !Objects.equals(actor.getId(),row.getMemberId())
                        || !seen.add(row.getRequestNo()))throw incomplete();
                result.add(project(row));
            }
            return new PageResult<>(total,result);
        });
    }
    private SeckillRequestReceiptDTO lookup(HttpServletRequest request,String store,String key,boolean byClient) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("原申请查询不能嵌套外层事务");
        if (!uuid(key)) throw exception0(400,"请提供原申请请求号");
        access.check(request,store);LoginUser actor=access.actor(); // 实际Host/店铺匹配，RPC在只读事务前。
        if (!id(actor.getTenantId())) throw exception0(403,"当前会员租户身份不正确");
        return read.execute(tx -> {
            LoginUser current=access.actor();
            if (!Objects.equals(actor.getTenantId(),current.getTenantId()) || !Objects.equals(actor.getId(),current.getId()))
                throw exception0(401,"查询身份已变化");
            SeckillRequestDO row=byClient?requests.ownClient(actor.getTenantId(),actor.getId(),key):requests.ownRequest(actor.getTenantId(),actor.getId(),key);
            SeckillRequestReceiptDTO result=new SeckillRequestReceiptDTO();result.setServerTime(System.currentTimeMillis());result.setFound(row!=null);
            if (row==null) return result; // 只说明未观察到已提交记录，不证明没有在途受理；禁止据此换号/释放预占。
            if (!Objects.equals(row.getTenantId(),actor.getTenantId()) || !Objects.equals(row.getMemberId(),actor.getId())
                    || !key.equals(byClient?row.getClientRequestId():row.getRequestNo())) throw incomplete();
            return project(row);
        });
    }
    /** 已在调用方核对本店本人；复用同一白名单投影，不在提交后再次做远程店铺调用。 */
    static SeckillRequestReceiptDTO project(SeckillRequestDO row) {
            verify(row);
            SeckillRequestReceiptDTO result=new SeckillRequestReceiptDTO();result.setFound(true);result.setServerTime(System.currentTimeMillis());
            result.setRequestNo(row.getRequestNo());result.setClientRequestId(row.getClientRequestId());result.setActivityId(row.getActivityId());
            result.setGeneration(row.getGeneration());result.setSkuId(row.getSkuId());result.setQuantity(row.getQuantity());
            result.setGoodsAmount(row.getGoodsAmount());result.setShippingAmount(row.getShippingAmount());result.setPayAmount(row.getPayAmount());
            result.setStatus(row.getStatus());result.setOrderId(row.getOrderId());result.setVersion(row.getVersion());
            result.setAcceptedAt(row.getAcceptedTime().atOffset(ZoneOffset.UTC));result.setReconcileDeadline(row.getReconcileDeadline().atOffset(ZoneOffset.UTC));
            result.setPayDeadline(row.getPayDeadline().atOffset(ZoneOffset.UTC));return result;
    }
    private static void verify(SeckillRequestDO row) {
        if (!uuid(row.getRequestNo()) || !uuid(row.getClientRequestId()) || !id(row.getActivityId()) || !id(row.getSkuId())
                || row.getGeneration()==null || row.getGeneration()<1 || row.getQuantity()==null || row.getQuantity()<1 || row.getQuantity()>9999
                || row.getPriceCent()==null || row.getPriceCent()<1 || row.getPriceCent()>2000000000
                || !money(row.getGoodsAmount()) || !money(row.getShippingAmount()) || !money(row.getPayAmount())
                || row.getGoodsAmount()!=((long)row.getPriceCent())*row.getQuantity() || row.getPayAmount()!=row.getGoodsAmount()+row.getShippingAmount()
                || !Arrays.asList(10,20,30,40,50,60).contains(row.getStatus()) || row.getOrderId()!=null && !id(row.getOrderId())
                || Integer.valueOf(20).equals(row.getStatus()) && row.getOrderId()==null
                || row.getAcceptedTime()==null || row.getReconcileDeadline()==null || row.getPayDeadline()==null
                || !row.getReconcileDeadline().equals(row.getAcceptedTime().plusSeconds(120)) || !row.getPayDeadline().equals(row.getAcceptedTime().plusMinutes(30))
                || row.getVersion()==null || row.getVersion()<0 || row.getVersion()>9_007_199_254_740_991L) throw incomplete();
    }
    private static boolean uuid(String x) { return x!=null && x.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"); }
    private static boolean id(Long x) { return x!=null && x>0 && x<=9_007_199_254_740_991L; }
    private static boolean money(Long x) { return x!=null && x>=0 && x<=9_007_199_254_740_991L; }
    private static cn.qinghe.mall.framework.common.exception.ServiceException incomplete() { return exception0(503,"原申请记录不完整，请按原请求号核查"); }
}
