package cn.qinghe.mall.module.product.service.search;
import cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import javax.validation.Validator;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** Adapted from macro EsProductServiceImpl.search keyword/brand/category/page/sort duties (Apache-2.0).
 * Java 8 JSON DSL, no caller-supplied DSL/field/index; tenant and visibility filters are mandatory.
 */
public final class ProductSearchQuery {
    private ProductSearchQuery(){}
    public static ObjectNode build(ObjectMapper json,Validator validator,Long tenant,AppProductSpuPageReqVO q) {
        if(tenant==null || tenant<=0 || tenant>9007199254740991L)throw exception0(400,"搜索租户不正确");
        validate(validator,q);
        ObjectNode out=json.createObjectNode();out.put("from",(q.getPageNo()-1)*q.getPageSize());out.put("size",q.getPageSize());
        out.put("track_total_hits",true);out.put("timeout","3s");out.putArray("_source").add("tenantId").add("id").add("version");
        ObjectNode bool=out.putObject("query").putObject("bool");ArrayNode filter=bool.putArray("filter");
        filter.addObject().putObject("term").put("tenantId",tenant);
        filter.addObject().putObject("term").put("visible",true);
        if(q.getBrandId()!=null)filter.addObject().putObject("term").put("brandId",q.getBrandId());
        if(q.getCategoryId()!=null)filter.addObject().putObject("term").put("categoryIds",q.getCategoryId());
        if(q.getMinPrice()!=null || q.getMaxPrice()!=null) {
            ObjectNode range=filter.addObject().putObject("range").putObject("price");
            if(q.getMinPrice()!=null)range.put("gte",q.getMinPrice());if(q.getMaxPrice()!=null)range.put("lte",q.getMaxPrice());
        }
        if(q.getKeyword()!=null && !q.getKeyword().trim().isEmpty()) {
            ObjectNode match=bool.putArray("must").addObject().putObject("multi_match");
            match.put("query",q.getKeyword().trim());match.put("type","best_fields");match.put("operator","and");
            match.putArray("fields").add("name^10").add("introduction^5").add("keywords^2").add("brandName^2").add("categoryName^2");
        }
        ArrayNode sort=out.putArray("sort");String field=q.getSortField();
        if(field!=null)sort.addObject().put("createTime".equals(field)?"createdAt":field,Boolean.FALSE.equals(q.getSortAsc())?"desc":"asc");
        else if(q.getKeyword()!=null && !q.getKeyword().trim().isEmpty())sort.addObject().put("_score","desc");
        sort.addObject().put("sort","desc");sort.addObject().put("id","desc");return out;
    }
    public static void validate(Validator validator,AppProductSpuPageReqVO q) {
        if(q==null || !validator.validate(q).isEmpty()
                || q.getPageNo()==null || q.getPageNo()<1 || q.getPageSize()==null || q.getPageSize()<1 || q.getPageSize()>20
                || (long)q.getPageNo()*q.getPageSize()>10000
                || q.getKeyword()!=null && q.getKeyword().chars().anyMatch(Character::isISOControl)
                || q.getMinPrice()!=null && q.getMaxPrice()!=null && q.getMinPrice()>q.getMaxPrice())throw exception0(400,"搜索条件超出支持范围");
    }

}
