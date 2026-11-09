package cn.qinghe.mall.module.product.service.spu;

import cn.hutool.crypto.digest.DigestUtil;
import cn.qinghe.mall.framework.common.biz.system.permission.dto.UserDataScopeRespDTO;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.excel.core.util.ExcelUtils;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.rpc.RemoteSystemSecurityClient;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductSpuExportVO;
import cn.qinghe.mall.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.qinghe.mall.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.qinghe.mall.module.product.dal.mysql.spu.ProductExportRecordMapper;
import cn.qinghe.mall.module.product.framework.export.ProductExportProperties;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import cn.qinghe.mall.module.product.service.catalog.ProductCatalogTransactions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Validator;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 择取原版独立export权限/筛选查询/Excel写出流程；按确认需求增加范围、资源上限和生成记录。
 * 同步有界任务，无公开文件链接；配额为本实例每租户一份、总量可配置，并非分布式调度。
 * @author 芋道源码
 */
@Service
public class ProductSpuExportService {
    private final ProductSpuMapper mapper;
    private final ProductExportRecordMapper records;
    private final ProductCatalogTransactions identities;
    private final SecurityFrameworkService security;
    private final RemoteSystemSecurityClient remote;
    private final ProductExportProperties limits;
    private final ProductSkuProperties productSettings;
    private final Validator validator;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate auditTransaction;
    private final Set<Long> activeTenants=new HashSet<>();

    public ProductSpuExportService(ProductSpuMapper mapper,ProductExportRecordMapper records,ProductCatalogTransactions identities,
            SecurityFrameworkService security,RemoteSystemSecurityClient remote,ProductExportProperties limits,
            ProductSkuProperties productSettings,Validator validator,PlatformTransactionManager manager) {
        this.mapper=mapper;this.records=records;this.identities=identities;this.security=security;this.remote=remote;
        this.limits=limits;this.productSettings=productSettings;this.validator=validator;
        readTransaction=new TransactionTemplate(manager);readTransaction.setReadOnly(true);
        readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);readTransaction.setTimeout(10);
        auditTransaction=new TransactionTemplate(manager);auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        auditTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);auditTransaction.setTimeout(5);
    }

    public void write(ProductSpuPageReqVO query,HttpServletResponse response) throws IOException {
        if (query==null || !validator.validate(query).isEmpty()) { throw exception0(400,"商品导出筛选不正确"); }
        LoginUser actor=identities.actor();
        UserDataScopeRespDTO scope=requireAccess();
        acquire(actor.getTenantId());
        try {
            List<ProductSpuExportVO> rows=readTransaction.execute(status -> readRows(query,scope));
            LimitedOutput output=new LimitedOutput(limits.getMaxFileBytes());
            try { ExcelUtils.write(output,"商品",ProductSpuExportVO.class,rows); }
            catch (RuntimeException ex) {
                if (output.exceeded) { throw exception0(400,"导出文件超过大小限制，请缩小筛选范围"); }
                throw new IllegalStateException("商品导出文件生成失败",ex);
            }
            if (output.exceeded) { throw exception0(400,"导出文件超过大小限制，请缩小筛选范围"); }
            requireUnchanged(scope);
            byte[] data=output.bytes();String id=UUID.randomUUID().toString(),hash=DigestUtil.sha256Hex(data);
            Map<String,Object> filters=new LinkedHashMap<>();filters.put("name",query.getName());filters.put("categoryId",query.getCategoryId());
            filters.put("brandId",query.getBrandId());filters.put("tabType",query.getTabType());filters.put("createTime",query.getCreateTime());
            filters.put("scope",scope);
            auditTransaction.execute(status -> {
                if (records.insertGenerated(id,actor.getTenantId(),actor.getId(),JsonUtils.toJsonString(filters),rows.size(),data.length,hash)!=1) {
                    throw new IllegalStateException("商品导出生成记录保存失败");
                }
                return null;
            });
            // 记录表示已生成，后续权限变化/网络中断均不能被描述为已成功下载。
            requireUnchanged(scope);
            response.setHeader("Cache-Control","no-store");
            response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader("Content-Disposition","attachment; filename=products.xlsx");
            response.setHeader("X-Export-Id",id);response.setHeader("X-Export-Rows",Integer.toString(rows.size()));
            response.setHeader("X-Export-Sha256",hash);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setContentLength(data.length);
            response.getOutputStream().write(data);
        } finally { release(actor.getTenantId()); }
    }

    private List<ProductSpuExportVO> readRows(ProductSpuPageReqVO query,UserDataScopeRespDTO scope) {
        Long count=mapper.selectCountByTab(query,scope,query.getTabType(),productSettings.getAlertStock());
        if (count==null || count<0) { throw new IllegalStateException("商品导出数量不可用"); }
        if (count>limits.getMaxRows()) { throw exception0(400,"符合条件的商品超过"+limits.getMaxRows()+"条，请缩小筛选范围后导出"); }
        List<ProductSpuExportVO> result=new ArrayList<>(count.intValue());Long cursor=null;
        while (true) {
            List<ProductSpuDO> batch=mapper.selectExportBatch(query,scope,productSettings.getAlertStock(),cursor);
            if (batch.isEmpty()) { break; }
            for (ProductSpuDO row:batch) {
                if (row.getId()==null || row.getId()<=0 || cursor!=null && row.getId()>=cursor || result.size()>=limits.getMaxRows()) {
                    throw new IllegalStateException("商品导出游标或数量异常");
                }
                ProductSpuExportVO value=BeanUtils.toBean(row,ProductSpuExportVO.class);
                value.setPrice(BigDecimal.valueOf(row.getPrice(),2));
                value.setStatus(Integer.valueOf(1).equals(row.getStatus())?"上架":Integer.valueOf(0).equals(row.getStatus())?"下架":"回收站");
                result.add(value);cursor=row.getId();
            }
            if (batch.size()<200) { break; }
        }
        if (result.size()!=count.intValue()) { throw new IllegalStateException("商品导出快照数量不一致"); }
        return result;
    }

    private UserDataScopeRespDTO requireAccess() {
        identities.actor();
        if (!security.hasPermission("product:spu:query") || !security.hasPermission("product:spu:export")) {
            throw exception0(403,"没有商品查询或导出权限");
        }
        return remote.currentDataScope();
    }
    private void requireUnchanged(UserDataScopeRespDTO scope) {
        if (!scope.equals(requireAccess())) { throw exception0(409,"商品数据范围已变化，请重新筛选后导出"); }
    }
    private synchronized void acquire(Long tenantId) {
        if (activeTenants.contains(tenantId)) { throw exception0(429,"当前商家已有商品导出正在处理"); }
        if (activeTenants.size()>=limits.getMaxConcurrent()) { throw exception0(429,"商品导出繁忙，请稍后重试"); }
        activeTenants.add(tenantId);
    }
    private synchronized void release(Long tenantId) { activeTenants.remove(tenantId); }

    /** 写满即停止，不先生成任意大文件再检查；Excel内部工作簿开销另由行/列上限约束。 */
    private static final class LimitedOutput extends OutputStream {
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private final int maximum;private boolean exceeded;
        private LimitedOutput(int maximum) { this.maximum=maximum; }
        @Override public void write(int value) throws IOException { ensure(1);output.write(value); }
        @Override public void write(byte[] data,int offset,int length) throws IOException {
            if (offset<0 || length<0 || offset>data.length-length) { throw new IndexOutOfBoundsException(); }
            ensure(length);output.write(data,offset,length);
        }
        private void ensure(int size) throws IOException {
            if (size>maximum-output.size()) { exceeded=true;throw new IOException("商品导出超过大小上限"); }
        }
        private byte[] bytes() { return output.toByteArray(); }
    }
}
