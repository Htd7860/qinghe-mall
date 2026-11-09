package cn.qinghe.mall.module.product.controller.admin.category;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.qinghe.mall.module.product.controller.admin.category.vo.ProductCategoryRespVO;
import cn.qinghe.mall.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.qinghe.mall.module.product.service.category.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Comparator;
import java.util.List;

import static cn.qinghe.mall.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品分类")
@RestController
@RequestMapping("/admin-api/product/category")
@Validated
public class ProductCategoryController {

    @ModelAttribute
    public void prepare(javax.servlet.http.HttpServletRequest request,
                        javax.servlet.http.HttpServletResponse response) {
        cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader("Cache-Control", "no-store");
    }


    @Resource
    private ProductCategoryService categoryService;

    @PostMapping("/create")
    @Operation(summary = "创建商品分类")
    @PreAuthorize("@ss.hasPermission('product:category:create')")
    public CommonResult<Long> createCategory(@Valid @RequestBody ProductCategorySaveReqVO createReqVO) {
        return success(categoryService.createCategory(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商品分类")
    @PreAuthorize("@ss.hasPermission('product:category:update')")
    public CommonResult<Boolean> updateCategory(@Valid @RequestBody ProductCategorySaveReqVO updateReqVO) {
        categoryService.updateCategory(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商品分类")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('product:category:delete')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") @javax.validation.constraints.Min(1) Long id,
            @RequestParam("expectedVersion") @javax.validation.constraints.Min(0) Long expectedVersion) {
        categoryService.deleteCategory(id, expectedVersion);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得商品分类")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:category:query')")
    public CommonResult<ProductCategoryRespVO> getCategory(@RequestParam("id") Long id) {
        ProductCategoryDO category = categoryService.getCategory(id);
        return success(BeanUtils.toBean(category, ProductCategoryRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得商品分类列表")
    @PreAuthorize("@ss.hasAnyPermissions('product:category:query','product:spu:create','product:spu:update')")
    public CommonResult<List<ProductCategoryRespVO>> getCategoryList(@Valid ProductCategoryListReqVO listReqVO) {
        List<ProductCategoryDO> list = categoryService.getCategoryList(listReqVO);
        list.sort(Comparator.comparing(ProductCategoryDO::getSort));
        return success(BeanUtils.toBean(list, ProductCategoryRespVO.class));
    }

}
