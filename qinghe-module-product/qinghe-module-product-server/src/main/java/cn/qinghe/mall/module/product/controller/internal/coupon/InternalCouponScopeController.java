package cn.qinghe.mall.module.product.controller.internal.coupon;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.module.product.api.coupon.dto.CouponProductScopeReqDTO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.dal.mysql.category.ProductCategoryMapper;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 原两种商品范围validateList职责合并为最小Boolean；原Bearer独立校验，不传目标租户。
 * @author 芋道源码
 */
@RestController @RequiredArgsConstructor
public class InternalCouponScopeController {
    private final ProductSpuMapper spus;
    private final ProductCategoryMapper categories;
    private final ProductCatalogTransactions actors;
    private final RemoteSystemSecurityClient security;
    @PostMapping("/internal-api/product/coupon-scope/validate")
    @PreAuthorize("@ss.hasAnyPermissions('promotion:coupon-template:create','promotion:coupon-template:update')")
    public CommonResult<Boolean> validate(@Valid @RequestBody CouponProductScopeReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try { return validateScope(body); }
        catch(cn.qinghe.mall.framework.common.exception.ServiceException rejected) {
            if(Integer.valueOf(400).equals(rejected.getCode()) || Integer.valueOf(404).equals(rejected.getCode())) {
                response.setStatus(200);return CommonResult.error(rejected.getCode(),rejected.getMessage());
            }
            throw rejected;
        }
    }
    private CommonResult<Boolean> validateScope(CouponProductScopeReqDTO body) {
        Long tenant=actors.actor().getTenantId();
        if(new HashSet<>(body.getIds()).size()!=body.getIds().size())throw exception0(400,"商品范围不能重复");
        if(body.getProductScope()==2) {
            UserDataScopeRespDTO scope=security.currentDataScope();
            List<ProductSpuDO> list=spus.selectList(new LambdaQueryWrapperX<ProductSpuDO>()
                    .eq(ProductSpuDO::getTenantId,tenant).in(ProductSpuDO::getId,body.getIds())
                    .select(ProductSpuDO::getId,ProductSpuDO::getOwnerUserId,ProductSpuDO::getOwnerDeptId,ProductSpuDO::getStatus));
            if(list.size()!=body.getIds().size() || list.stream().anyMatch(x -> Integer.valueOf(-1).equals(x.getStatus()) || !scope.includes(x.getOwnerUserId(),x.getOwnerDeptId())))throw exception0(404,"商品范围不存在或无权配置");
        } else {
            Long count=categories.selectCount(new LambdaQueryWrapperX<ProductCategoryDO>().eq(ProductCategoryDO::getTenantId,tenant)
                    .in(ProductCategoryDO::getId,body.getIds()).eq(ProductCategoryDO::getStatus,0));
            if(count!=body.getIds().size())throw exception0(404,"分类范围不存在或已停用");
        }
        return CommonResult.success(true);
    }
}
