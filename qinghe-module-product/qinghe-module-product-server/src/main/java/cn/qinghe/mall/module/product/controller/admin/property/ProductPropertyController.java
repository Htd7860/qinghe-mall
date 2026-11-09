package cn.qinghe.mall.module.product.controller.admin.property;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.module.product.controller.admin.property.vo.property.ProductPropertyPageReqVO;
import cn.qinghe.mall.module.product.controller.admin.property.vo.property.ProductPropertyRespVO;
import cn.qinghe.mall.module.product.controller.admin.property.vo.property.ProductPropertySaveReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.property.ProductPropertyDO;
import cn.qinghe.mall.module.product.service.property.ProductPropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.qinghe.mall.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品属性项")
@RestController
@RequestMapping("/admin-api/product/property")
@Validated
public class ProductPropertyController {

    @ModelAttribute
    public void prepare(javax.servlet.http.HttpServletRequest request,
                        javax.servlet.http.HttpServletResponse response) {
        cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader("Cache-Control", "no-store");
    }


    @Resource
    private ProductPropertyService productPropertyService;

    @PostMapping("/create")
    @Operation(summary = "创建属性项")
    @PreAuthorize("@ss.hasPermission('product:property:create')")
    public CommonResult<Long> createProperty(@Valid @RequestBody ProductPropertySaveReqVO createReqVO) {
        return success(productPropertyService.createProperty(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新属性项")
    @PreAuthorize("@ss.hasPermission('product:property:update')")
    public CommonResult<Boolean> updateProperty(@Valid @RequestBody ProductPropertySaveReqVO updateReqVO) {
        productPropertyService.updateProperty(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除属性项")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:property:delete')")
    public CommonResult<Boolean> deleteProperty(@RequestParam("id") @javax.validation.constraints.Min(1) Long id,
            @RequestParam("expectedVersion") @javax.validation.constraints.Min(0) Long expectedVersion) {
        productPropertyService.deleteProperty(id, expectedVersion);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得属性项")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:property:query')")
    public CommonResult<ProductPropertyRespVO> getProperty(@RequestParam("id") Long id) {
        ProductPropertyDO property = productPropertyService.getProperty(id);
        return success(BeanUtils.toBean(property, ProductPropertyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得属性项分页")
    @PreAuthorize("@ss.hasPermission('product:property:query')")
    public CommonResult<PageResult<ProductPropertyRespVO>> getPropertyPage(@Valid ProductPropertyPageReqVO pageVO) {
        PageResult<ProductPropertyDO> pageResult = productPropertyService.getPropertyPage(pageVO);
        return success(BeanUtils.toBean(pageResult, ProductPropertyRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得属性项精简列表")
    @PreAuthorize("@ss.hasAnyPermissions('product:property:query','product:spu:create','product:spu:update')")
    public CommonResult<List<ProductPropertyRespVO>> getPropertySimpleList() {
        List<ProductPropertyDO> list = productPropertyService.getPropertyList();
        return success(BeanUtils.toBean(list, ProductPropertyRespVO.class));
    }

}
