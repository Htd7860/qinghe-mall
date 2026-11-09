package cn.qinghe.mall.module.product.controller.internal.seckill;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.module.product.api.seckill.dto.*;
import cn.qinghe.mall.module.product.api.trade.dto.ProductTradeSkuRespDTO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/**
 * 择取原 validateProductExists 的 SPU/SKU 校验，复用现有 coupon-scope 的原 Bearer/数据范围边界。
 * 内部只读路径不经 Gateway；普通后台账号仍独立认证，不能把 tenant 头当作身份。
 * @author halfninety
 */
@RestController
@Validated
@RequiredArgsConstructor
public class InternalSeckillProductController {
    private final ProductSpuMapper spus;
    private final ProductSkuMapper skus;
    private final ProductCatalogTransactions actors;
    private final RemoteSystemSecurityClient security;
    private final PlatformTransactionManager manager;

    @PostMapping("/internal-api/product/seckill/snapshot")
    @PreAuthorize("@ss.hasAnyPermissions('promotion:seckill-activity:create','promotion:seckill-activity:update','promotion:seckill-activity:publish')")
    public CommonResult<SeckillProductSnapshotRespDTO> snapshot(
            @Valid @RequestBody SeckillProductSnapshotReqDTO body,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        Long tenant=actors.actor().getTenantId();
        if (new HashSet<>(body.getSkuIds()).size()!=body.getSkuIds().size())
            throw exception0(400,"活动规格不能重复");
        // 远程数据范围查询在本地读事务之前；后续本地读取保持同一快照。
        UserDataScopeRespDTO scope=security.currentDataScope();
        TransactionTemplate read=new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);read.setTimeout(5);
        try { return CommonResult.success(read.execute(tx -> {
            ProductSpuDO spu=spus.selectOne(new LambdaQueryWrapperX<ProductSpuDO>()
                    .eq(ProductSpuDO::getTenantId,tenant).eq(ProductSpuDO::getId,body.getSpuId()));
            if (spu==null || !Integer.valueOf(1).equals(spu.getStatus()) || scope==null
                    || !scope.includes(spu.getOwnerUserId(),spu.getOwnerDeptId()))
                throw exception0(404,"活动商品不存在、未上架或不在当前数据范围内");
            List<ProductSkuDO> items=skus.selectList(new LambdaQueryWrapperX<ProductSkuDO>()
                    .eq(ProductSkuDO::getTenantId,tenant).eq(ProductSkuDO::getSpuId,spu.getId())
                    .eq(ProductSkuDO::getStatus,0).in(ProductSkuDO::getId,body.getSkuIds()).orderByAsc(ProductSkuDO::getId));
            if (items.size()!=body.getSkuIds().size()) throw exception0(404,"活动规格不存在或已停用");
            SeckillProductSnapshotRespDTO result=new SeckillProductSnapshotRespDTO();
            result.setTenantId(tenant);result.setSpuId(spu.getId());result.setSpuVersion(spu.getAuthVersion());
            result.setProductName(spu.getName());result.setPicUrl(spu.getPicUrl());
            List<SeckillProductSnapshotRespDTO.Sku> selected=new ArrayList<>();
            for (ProductSkuDO sku:items) {
                SeckillProductSnapshotRespDTO.Sku item=new SeckillProductSnapshotRespDTO.Sku();
                item.setSkuId(sku.getId());item.setSkuVersion(sku.getAuthVersion());item.setPrice(sku.getPrice());
                item.setProperties(BeanUtils.toBean(sku.getProperties(),ProductTradeSkuRespDTO.Property.class));selected.add(item);
            }
            result.setSkus(selected);return result;
        })); } catch (cn.qinghe.mall.framework.common.exception.ServiceException rejected) {
            if (Integer.valueOf(400).equals(rejected.getCode()) || Integer.valueOf(404).equals(rejected.getCode())) {
                response.setStatus(200);return CommonResult.error(rejected.getCode(),rejected.getMessage());
            }
            throw rejected;
        }
    }
}
