package cn.qinghe.mall.framework.tenant.core.db;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.ForMode;

/** JSqlParser 4.9 serializes top-level locking clauses before ORDER/LIMIT.
 * Keep the original tenant AST rewrite, render the MySQL locking suffix last.
 * No SQL text replacement and no tenant-ignore bypass. Remove when parser is upgraded.
 */
public class MySqlTenantLineInnerInterceptor extends TenantLineInnerInterceptor {
    public MySqlTenantLineInnerInterceptor(TenantLineHandler handler) { super(handler); }

    @Override
    protected String processParser(Statement statement,int index,String sql,Object context) {
        if (!(statement instanceof PlainSelect)) return super.processParser(statement,index,sql,context);
        PlainSelect select=(PlainSelect)statement;
        ForMode mode=select.getForMode();
        if (mode==null) return super.processParser(statement,index,sql,context);
        if ((mode!=ForMode.UPDATE && mode!=ForMode.SHARE) || select.getForUpdateTable()!=null
                || select.getWait()!=null || select.getForClause()!=null || select.getIsolation()!=null
                || select.isNoWait() && select.isSkipLocked()) {
            throw new IllegalStateException("Unsupported MySQL locking clause");
        }
        boolean noWait=select.isNoWait(),skipLocked=select.isSkipLocked();
        select.setForMode(null);select.setNoWait(false);select.setSkipLocked(false);
        try {
            String rewritten=super.processParser(statement,index,sql,context);
            return rewritten+" FOR "+mode.getValue()+(noWait?" NOWAIT":skipLocked?" SKIP LOCKED":"");
        } finally {
            select.setForMode(mode);select.setNoWait(noWait);select.setSkipLocked(skipLocked);
        }
    }
}
