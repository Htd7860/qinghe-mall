package cn.qinghe.mall.module.trade.service.order;

import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.*;
import cn.qinghe.mall.module.trade.dal.mysql.order.TradeTenantJobCursorMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** DEC-077 exact isolated database, reserved shard fixture, worker transactions always roll back. */
@EnabledIfEnvironmentVariable(named="QINGHE_SECKILL_TEST_ENABLED",matches="true")
class TradeJobCursorConcurrencyTest {
    @Test void duplicateInitializationLocksWithoutResettingProgressOrLease() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:3306/qinghe_trade_seckill_test?serverTimezone=UTC",
                System.getenv().getOrDefault("QINGHE_DB_USERNAME", "root"), System.getenv("QINGHE_DB_PASSWORD"));
        try (java.sql.Connection c=ds.getConnection()) { assertThat(c.getCatalog()).isEqualTo("qinghe_trade_seckill_test"); }
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String owner="00000000-0000-4000-8000-000000000077";
        jdbc.update("INSERT INTO trade_tenant_job_cursor(job_code,shard_index,shard_total,last_tenant_id,ceiling_tenant_id,lease_owner,lease_until,update_time) "
                + "VALUES('ORDER_RECOVERY',127,128,73000001,73000002,?,'2030-01-01','2026-01-01') ON DUPLICATE KEY UPDATE job_code=job_code",owner);
        MybatisConfiguration cfg = new MybatisConfiguration(); cfg.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(cfg);factory.setPlugins(plugin);
        org.apache.ibatis.session.SqlSessionFactory sf=factory.getObject();sf.getConfiguration().addMapper(TradeTenantJobCursorMapper.class);
        TradeTenantJobCursorMapper cursor=new SqlSessionTemplate(sf).getMapper(TradeTenantJobCursorMapper.class);
        CyclicBarrier start=new CyclicBarrier(2);ExecutorService workers=Executors.newFixedThreadPool(2);
        Callable<Void> work=()->{
            try { new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
                tx.setRollbackOnly();
                try {start.await(3,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException(error);}
                cursor.initialize("ORDER_RECOVERY",127,128,LocalDateTime.of(2026,11,1,0,0));
                try {Thread.sleep(200);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                cn.qinghe.mall.module.trade.dal.dataobject.order.TradeTenantJobCursorDO row=cursor.lock("ORDER_RECOVERY",127,128);
                assertThat(row.getLastTenantId()).isEqualTo(73000001L);assertThat(row.getCeilingTenantId()).isEqualTo(73000002L);
                assertThat(row.getLeaseOwner()).isEqualTo(owner);assertThat(row.getLeaseUntil()).isEqualTo(LocalDateTime.of(2030,1,1,0,0));
                assertThat(row.getUpdateTime()).isEqualTo(LocalDateTime.of(2026,1,1,0,0));
            });return null;}finally{TenantContextHolder.clear();}
        };
        try {Future<Void>a=workers.submit(work),b=workers.submit(work);a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}finally{workers.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT last_tenant_id FROM trade_tenant_job_cursor WHERE job_code='ORDER_RECOVERY' AND shard_index=127 AND shard_total=128",Long.class)).isEqualTo(73000001L);
    }
}
