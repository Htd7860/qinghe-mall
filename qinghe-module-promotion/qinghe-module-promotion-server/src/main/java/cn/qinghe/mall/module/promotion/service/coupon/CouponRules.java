package cn.qinghe.mall.module.promotion.service.coupon;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.module.promotion.controller.admin.coupon.vo.template.CouponTemplateBaseVO;
import cn.qinghe.mall.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.validation.Validator;
import java.util.*;
import java.time.temporal.ChronoUnit;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 择取原BaseVO条件校验和validateTotalCountNotLessThanTakeLimitCount，集中规范化券规则。
 * 商品/分类真实归属由后续模板写入边界调用product API校验，本类不把格式合法冒充对象存在。
 * @author 芋道源码
 */
@Service @RequiredArgsConstructor
public class CouponRules {
    private final Validator validator;
    public CouponTemplateDO normalize(CouponTemplateBaseVO body) {
        if(body==null || !validator.validate(body).isEmpty())throw invalid();
        if(!unlimitedOrPositive(body.getTotalCount()) || !unlimitedOrPositive(body.getTakeLimitCount())
                || !oneOf(body.getTakeType(),1,2) || !oneOf(body.getProductScope(),1,2,3)
                || !oneOf(body.getValidityType(),1,2) || !oneOf(body.getDiscountType(),1,2)
                || !money(body.getUsePrice(),false))throw invalid();
        if(body.getTotalCount()!=-1 && body.getTakeLimitCount()!=-1 && body.getTakeLimitCount()>body.getTotalCount())throw invalid();
        List<Long> ids=body.getProductScopeValues()==null?new ArrayList<>():new ArrayList<>(body.getProductScopeValues());
        if(ids.size()>200 || ids.stream().anyMatch(id -> id==null || id<=0) || new HashSet<>(ids).size()!=ids.size()
                || (body.getProductScope()==1?!ids.isEmpty():ids.isEmpty()))throw invalid();
        Collections.sort(ids);
        CouponTemplateDO row=BeanUtils.toBean(body,CouponTemplateDO.class);
        // Update/response子类可能带id或统计字段；规则投影不接收这些元数据。
        row.setId(null);row.setStatus(null);row.setAuthVersion(null);row.setTakeCount(null);row.setUseCount(null);
        row.setName(body.getName().trim());
        if(row.getName().isEmpty())throw invalid();
        row.setDescription(body.getDescription()==null?"":body.getDescription());row.setProductScopeValues(ids);
        if(body.getValidityType()==1) {
            if(body.getValidStartTime()==null || body.getValidEndTime()==null
                    || body.getValidStartTime().getYear()<1000 || body.getValidEndTime().getYear()>9999)throw invalid();
            row.setValidStartTime(body.getValidStartTime().truncatedTo(ChronoUnit.MICROS));row.setValidEndTime(body.getValidEndTime().truncatedTo(ChronoUnit.MICROS));
            if(!row.getValidStartTime().isBefore(row.getValidEndTime()))throw invalid();
            row.setFixedStartTerm(null);row.setFixedEndTerm(null);
        } else {
            if(!Integer.valueOf(0).equals(body.getFixedStartTerm()) || body.getFixedEndTerm()==null || body.getFixedEndTerm()<1 || body.getFixedEndTerm()>3650)throw invalid();
            row.setValidStartTime(null);row.setValidEndTime(null);
        }
        if(body.getDiscountType()==1) {
            if(!money(body.getDiscountPrice(),true))throw invalid();
            row.setDiscountPercent(null);row.setDiscountLimitPrice(null);
        } else {
            if(body.getDiscountPercent()==null || body.getDiscountPercent()<1 || body.getDiscountPercent()>99 || !money(body.getDiscountLimitPrice(),true))throw invalid();
            row.setDiscountPrice(null);
        }
        return row;
    }
    public void validateStored(CouponTemplateDO row) {
        try { normalize(BeanUtils.toBean(row,CouponTemplateBaseVO.class)); }
        catch(cn.qinghe.mall.framework.common.exception.ServiceException invalid) { throw exception0(503,"优惠券模板数据异常"); }
        if(row.getId()==null || row.getId()<=0 || row.getAuthVersion()==null || row.getAuthVersion()<0
                || !oneOf(row.getStatus(),0,1) || row.getTakeCount()==null || row.getTakeCount()<0 || row.getTakeCount()>2000000000
                || row.getUseCount()==null || row.getUseCount()<0 || row.getUseCount()>row.getTakeCount()
                || row.getTotalCount()!=-1 && row.getTakeCount()>row.getTotalCount())throw exception0(503,"优惠券模板数据异常");
    }
    private boolean oneOf(Integer value,Integer... values){return value!=null && Arrays.asList(values).contains(value);}
    private boolean unlimitedOrPositive(Integer value){return value!=null && (value==-1 || value>=1 && value<=2000000000);}
    private boolean money(Integer value,boolean positive){return value!=null && value>=(positive?1:0) && value<=2000000000;}
    private cn.qinghe.mall.framework.common.exception.ServiceException invalid(){return exception0(400,"优惠券额度、范围、有效期或优惠规则不正确");}
}
