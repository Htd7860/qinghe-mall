package cn.qinghe.mall.framework.tenant.core.db;

import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.toolkit.SqlParserUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.tenant.core.db
 * @Project：qinghe-mall
 * @name：TenantDatabaseInterceptor
 * @Date：2026/10/21 17:35
 * @Filename：TenantDatabaseInterceptor
 */
public class TenantDatabaseInterceptor implements TenantLineHandler {

    private final Set<String> ignoresTables=new HashSet<>();

    public TenantDatabaseInterceptor(TenantProperties properties){
        if(properties.getIgnoreTables()!=null){
            properties.getIgnoreTables().forEach(table->ignoresTables.add(normalize(table)));
        }
    }
    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContextHolder.getRequiredTenantId());
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if(TenantContextHolder.isIgnore())return true;

        String name=SqlParserUtils.removeWrapperSymbol(tableName);
        TableInfo tableInfo = TableInfoHelper.getTableInfo(name);

        if(tableInfo!=null&&TenantBaseDo.class.isAssignableFrom(tableInfo.getEntityType())){
            return false;
        }

        if(ignoresTables.contains(normalize(name))){
            return true;
        }
        if (tableInfo != null) {
            TenantIgnore annotation =
                    tableInfo.getEntityType().getAnnotation(TenantIgnore.class);

            if (annotation != null) {
                if ("true".equals(annotation.enable())) {
                    return true;
                }
                if ("false".equals(annotation.enable())) {
                    return false;
                }
                throw new IllegalStateException(
                        "实体上的 TenantIgnore.enable 仅支持 true 或 false");
            }
        }

        // 未识别的表也不自动放行
        return false;
    }

    private static String normalize(String table){
        return SqlParserUtils.removeWrapperSymbol(table).toLowerCase(Locale.ROOT);
    }

}
