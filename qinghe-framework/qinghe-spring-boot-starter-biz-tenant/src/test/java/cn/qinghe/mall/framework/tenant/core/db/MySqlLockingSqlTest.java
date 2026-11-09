package cn.qinghe.mall.framework.tenant.core.db;
import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MySqlLockingSqlTest {
 @Test void keepsOrderLimitAndLockSuffix(){verify("SELECT * FROM trade_order_item ORDER BY id LIMIT 201 FOR UPDATE","ORDER BY id LIMIT 201 FOR UPDATE");}
 @Test void keepsSkipLocked(){verify("SELECT * FROM trade_order_outbox ORDER BY available_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED","LIMIT 1 FOR UPDATE SKIP LOCKED");}
 @Test void keepsShareNowait(){verify("SELECT * FROM trade_order ORDER BY id FOR SHARE NOWAIT","ORDER BY id FOR SHARE NOWAIT");}
 @Test void keepsLiteralAndJoinPredicates(){String sql=rewrite("SELECT a.* FROM trade_order a JOIN trade_order_item b ON a.id=b.order_id WHERE a.user_remark=' FOR UPDATE ORDER BY ' ORDER BY a.id LIMIT 2 FOR UPDATE");assertThat(sql).contains("' FOR UPDATE ORDER BY '","a.tenant_id = 72000001","b.tenant_id = 72000001").endsWith("LIMIT 2 FOR UPDATE");}
 @Test void keepsOrdinaryQueryUnlocked(){assertThat(rewrite("SELECT * FROM trade_order ORDER BY id LIMIT 2")).contains("tenant_id = 72000001").endsWith("LIMIT 2").doesNotContain("FOR UPDATE");}
 @Test void restoresAstOnRepeatedUse(){MySqlTenantLineInnerInterceptor i=new MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()));TenantContextHolder.setTenantId(72000001L);try{String sql="SELECT * FROM trade_order ORDER BY id FOR UPDATE";assertThat(i.parserSingle(sql,null)).isEqualTo(i.parserSingle(sql,null));}finally{TenantContextHolder.clear();}}
 private void verify(String input,String suffix){assertThat(rewrite(input)).contains("tenant_id = 72000001").endsWith(suffix);}
 private String rewrite(String sql){TenantContextHolder.setTenantId(72000001L);try{return new MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())).parserSingle(sql,null);}finally{TenantContextHolder.clear();}}
}
