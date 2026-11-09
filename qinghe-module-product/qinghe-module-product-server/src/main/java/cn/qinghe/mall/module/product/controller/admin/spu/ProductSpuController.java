package cn.qinghe.mall.module.product.controller.admin.spu;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.*;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import cn.qinghe.mall.module.product.service.spu.ProductSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

import static cn.qinghe.mall.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商品 SPU")
@RestController
@RequestMapping("/admin-api/product/spu")
@Validated
public class ProductSpuController {
    @ModelAttribute
    public void prepare(javax.servlet.http.HttpServletRequest request,javax.servlet.http.HttpServletResponse response) {
        cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils.requireBackendRealm(request);
        response.setHeader("Cache-Control","no-store");
    }


    @Resource
    private ProductSpuService productSpuService;

    @Resource
    private ProductSkuProperties settings;

    @Resource
    private cn.qinghe.mall.module.product.service.spu.ProductSpuExportService exports;

    @GetMapping("/export-excel")
    @Operation(summary = "导出当前数据范围内的商品")
    @PreAuthorize("@ss.hasPermission('product:spu:export')")
    public void exportSpuList(@Valid ProductSpuPageReqVO query,javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        exports.write(query,response);
    }

    @GetMapping("/editor-options")
    @Operation(summary = "获得商品编辑器规格数量限制")
    @PreAuthorize("@ss.hasAnyPermissions('product:spu:create','product:spu:update')")
    public CommonResult<ProductEditorOptionsRespVO> editorOptions() {
        ProductEditorOptionsRespVO result = new ProductEditorOptionsRespVO();
        result.setMaxSkus(settings.getMaxSkus());
        result.setMaxDimensions(20);
        return success(result);
    }

    @GetMapping("/owner-option")
    @Operation(summary = "核对目标员工真实商品归属")
    @PreAuthorize("@ss.hasPermission('product:spu:transfer')")
    public CommonResult<cn.qinghe.mall.framework.common.biz.system.permission.dto.ProductOwnerRespDTO> ownerOption(
            @RequestParam("userId") @javax.validation.constraints.Min(1) Long userId) {
        return success(productSpuService.getOwnerOption(userId));
    }

    @PutMapping("/transfer-owner")
    @Operation(summary = "转移商品归属")
    @PreAuthorize("@ss.hasPermission('product:spu:transfer')")
    public CommonResult<Boolean> transferOwner(@Valid @RequestBody ProductSpuTransferReqVO body) {
        productSpuService.transferOwner(body);return success(true);
    }

    @PostMapping("/create")
    @Operation(summary = "创建商品 SPU")
    @PreAuthorize("@ss.hasPermission('product:spu:create')")
    public CommonResult<Long> createProductSpu(@Valid @RequestBody ProductSpuSaveReqVO createReqVO) {
        return success(productSpuService.createSpu(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商品 SPU")
    @PreAuthorize("@ss.hasPermission('product:spu:update')")
    public CommonResult<Boolean> updateSpu(@Valid @RequestBody ProductSpuSaveReqVO updateReqVO) {
        productSpuService.updateSpu(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新商品 SPU Status")
    @PreAuthorize("@ss.hasPermission('product:spu:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody ProductSpuUpdateStatusReqVO updateReqVO) {
        productSpuService.updateSpuStatus(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商品 SPU")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:spu:delete')")
    public CommonResult<Boolean> deleteSpu(@RequestParam("id") @javax.validation.constraints.Min(1) Long id,
            @RequestParam("expectedVersion") @javax.validation.constraints.Min(0) Long expectedVersion) {
        productSpuService.deleteSpu(id,expectedVersion);
        return success(true);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得商品 SPU 明细")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<ProductSpuRespVO> getSpuDetail(@RequestParam("id") @javax.validation.constraints.Min(1) Long id) {
        return success(productSpuService.getSpuDetail(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商品 SPU 分页")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<PageResult<ProductSpuRespVO>> getSpuPage(@Valid ProductSpuPageReqVO pageVO) {
        PageResult<ProductSpuDO> pageResult = productSpuService.getSpuPage(pageVO);
        return success(BeanUtils.toBean(pageResult, ProductSpuRespVO.class));
    }

    @GetMapping("/get-count")
    @Operation(summary = "获得商品 SPU 分页 tab count（支持按 name/categoryId/createTime 筛选）")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<Map<Integer, Long>> getSpuCount(@Valid ProductSpuPageReqVO reqVO) {
        return success(productSpuService.getTabsCount(reqVO));
    }

}
