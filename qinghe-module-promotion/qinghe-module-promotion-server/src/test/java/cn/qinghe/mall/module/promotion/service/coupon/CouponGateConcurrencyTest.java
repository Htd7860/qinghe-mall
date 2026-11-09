package cn.qinghe.mall.module.promotion.service.coupon;
import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.*;
import cn.qinghe.mall.module.promotion.dal.mysql.coupon.CouponOrderMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
/** Exact isolated DB; keep synthetic fixture gate, rollback worker transactions, never delete business rows. */
@EnabledIfEnvironmentVariable(named="QINGHE_COMMERCE_TEST_ENABLED",matches="true")
class CouponGateConcurrencyTest {
 @Test void repeatedGateRegistrationSerializesWithoutSharedLockUpgradeDeadlock() throws Exception {
  String url="jdbc:mysql://127.0.0.1:3306/qinghe_promotion_commerce_test?serverTimezone=Asia/Shanghai";
  DriverManagerDataSource ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("QINGHE_DB_USERNAME","root"),System.getenv("QINGHE_DB_PASSWORD"));
  try(java.sql.Connection c=ds.getConnection()){assertThat(c.getCatalog()).isEqualTo("qinghe_promotion_commerce_test");}
  MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
  MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
  MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(cfg);factory.setPlugins(plugin);
  GlobalConfig g=new GlobalConfig();g.setBanner(false);g.setDbConfig(new GlobalConfig.DbConfig());factory.setGlobalConfig(g);
  org.apache.ibatis.session.SqlSessionFactory sf=factory.getObject();sf.getConfiguration().addMapper(CouponOrderMapper.class);
  CouponOrderMapper mapper=new SqlSessionTemplate(sf).getMapper(CouponOrderMapper.class);JdbcTemplate jdbc=new JdbcTemplate(ds);
  long order=8_900_000_000L;
  jdbc.update("INSERT IGNORE INTO promotion_coupon_order_gate(tenant_id,order_id,user_id,coupon_id,closed) VALUES(72000001,?,72000001001,1,b'0')",order);
  CyclicBarrier start=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
  Callable<Void> work=()->{TenantContextHolder.setTenantId(72000001L);try{new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
   tx.setRollbackOnly();try{start.await(3,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
   int inserted=mapper.ensureGate(72000001L,order,72000001001L,1L);assertThat(inserted).isIn(0,1);
   try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
   assertThat(mapper.lockGate(72000001L,order).getUserId()).isEqualTo(72000001001L);
  });return null;}finally{TenantContextHolder.clear();}};
  try{Future<Void> a=pool.submit(work),b=pool.submit(work);a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_coupon_order_gate WHERE tenant_id=72000001 AND order_id=? AND user_id=72000001001 AND coupon_id=1 AND closed=b'0'",Integer.class,order)).isEqualTo(1);
 }
}
