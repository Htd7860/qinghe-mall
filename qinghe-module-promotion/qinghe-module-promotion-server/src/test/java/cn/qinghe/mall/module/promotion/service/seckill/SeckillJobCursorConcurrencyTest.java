package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillTenantJobCursorMapper;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
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

/** DEC-077 reserved fixture keys only; actual MP/tenant parser/MySQL and rollback worker transactions. */
@EnabledIfEnvironmentVariable(named="QINGHE_SECKILL_TEST_ENABLED",matches="true")
class SeckillJobCursorConcurrencyTest {
    private DriverManagerDataSource ds;private SeckillTenantJobCursorMapper cursor;
    private static final String ORIGINAL="00000000-0000-4000-8000-000000000077";
    @BeforeEach void setup() throws Exception {
        ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/qinghe_promotion_seckill_test?serverTimezone=UTC",System.getenv().getOrDefault("QINGHE_DB_USERNAME","root"),System.getenv("QINGHE_DB_PASSWORD"));
        try(java.sql.Connection c=ds.getConnection()){assertThat(c.getCatalog()).isEqualTo("qinghe_promotion_seckill_test");}
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        jdbc.update("INSERT INTO promotion_seckill_job_cursor(job_code,shard_index,shard_total,last_tenant_id,ceiling_tenant_id,lease_owner,lease_until,update_time) VALUES('SECKILL_COMMAND_PUBLISH',127,128,73000001,73000002,?,'2030-01-01','2026-01-01') ON DUPLICATE KEY UPDATE job_code=job_code",ORIGINAL);
        jdbc.update("INSERT INTO promotion_seckill_cache_cursor(tenant_id,version,update_time,after_time,after_event,after_request,after_return,after_pool_id,after_expiry_activity,after_clearing_activity) VALUES(73000999,7,'2026-01-01','2026-01-01',?,?,?,17,18,19) ON DUPLICATE KEY UPDATE tenant_id=tenant_id",ORIGINAL,ORIGINAL,ORIGINAL);
        MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(cfg);factory.setPlugins(plugin);
        org.apache.ibatis.session.SqlSessionFactory sf=factory.getObject();sf.getConfiguration().addMapper(SeckillTenantJobCursorMapper.class);cursor=new SqlSessionTemplate(sf).getMapper(SeckillTenantJobCursorMapper.class);
    }
    @Test void duplicateJobCursorKeepsOriginalLeaseAndPosition() throws Exception {
        race(()->{cursor.initialize("SECKILL_COMMAND_PUBLISH",127,128,LocalDateTime.of(2026,11,1,0,0));pause();
            SeckillTenantJobCursorDO row=cursor.lock("SECKILL_COMMAND_PUBLISH",127,128);
            assertThat(row.getLastTenantId()).isEqualTo(73000001L);assertThat(row.getCeilingTenantId()).isEqualTo(73000002L);
            assertThat(row.getLeaseOwner()).isEqualTo(ORIGINAL);assertThat(row.getLeaseUntil()).isEqualTo(LocalDateTime.of(2030,1,1,0,0));assertThat(row.getUpdateTime()).isEqualTo(LocalDateTime.of(2026,1,1,0,0));});
    }
    @Test void duplicateCacheCursorKeepsVersionAndEveryKeysetPosition() throws Exception {
        race(()->{cursor.initializeCache(73000999L);pause();SeckillCacheJobCursorDO row=cursor.lockCache(73000999L);
            assertThat(row.getVersion()).isEqualTo(7L);assertThat(row.getAfterTime()).isEqualTo(LocalDateTime.of(2026,1,1,0,0));assertThat(row.getAfterEvent()).isEqualTo(ORIGINAL);
            assertThat(row.getAfterRequest()).isEqualTo(ORIGINAL);assertThat(row.getAfterReturn()).isEqualTo(ORIGINAL);assertThat(row.getAfterPoolId()).isEqualTo(17L);
            assertThat(row.getAfterExpiryActivity()).isEqualTo(18L);assertThat(row.getAfterClearingActivity()).isEqualTo(19L);assertThat(row.getUpdateTime()).isEqualTo(LocalDateTime.of(2026,1,1,0,0));});
    }
    private void race(Runnable action) throws Exception {
        CyclicBarrier start=new CyclicBarrier(2);ExecutorService workers=Executors.newFixedThreadPool(2);
        Callable<Void> task=()->{TenantContextHolder.setTenantId(73000999L);try{new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{tx.setRollbackOnly();try{start.await(3,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException(error);}action.run();});return null;}finally{TenantContextHolder.clear();}};
        try{Future<Void>a=workers.submit(task),b=workers.submit(task);a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}finally{workers.shutdownNow();}
    }
    private void pause(){try{Thread.sleep(200);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
}
