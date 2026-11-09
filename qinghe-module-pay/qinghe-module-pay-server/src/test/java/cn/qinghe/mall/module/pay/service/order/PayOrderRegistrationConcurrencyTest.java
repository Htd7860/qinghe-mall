package cn.qinghe.mall.module.pay.service.order;
import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.*;
import cn.qinghe.mall.module.pay.dal.mysql.order.PayOrderMapper;
import cn.qinghe.mall.module.pay.dal.dataobject.order.PayOrderDO;
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
import java.util.concurrent.*;import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
/** Real MP/MySQL on the exact DEC-076 test DB; synthetic closed fixture retained, workers roll back. */
@EnabledIfEnvironmentVariable(named="QINGHE_COMMERCE_TEST_ENABLED",matches="true")
class PayOrderRegistrationConcurrencyTest {
 @Test void duplicateCloseRegistrationDoesNotDeadlock() throws Exception { verify(true,"8990000001"); }
 @Test void duplicateCreateCannotReopenClosedOriginal() throws Exception { verify(false,"8990000002"); }
 private void verify(boolean close,String order) throws Exception {
  String url="jdbc:mysql://127.0.0.1:3306/qinghe_pay_commerce_test?serverTimezone=Asia/Shanghai";
  DriverManagerDataSource ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("QINGHE_DB_USERNAME","root"),System.getenv("QINGHE_DB_PASSWORD"));
  try(java.sql.Connection c=ds.getConnection()){assertThat(c.getCatalog()).isEqualTo("qinghe_pay_commerce_test");}
  MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
  MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
  MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(cfg);factory.setPlugins(plugin);
  GlobalConfig g=new GlobalConfig();g.setBanner(false);g.setDbConfig(new GlobalConfig.DbConfig());factory.setGlobalConfig(g);
  org.apache.ibatis.session.SqlSessionFactory sf=factory.getObject();sf.getConfiguration().addMapper(PayOrderMapper.class);
  PayOrderMapper mapper=new SqlSessionTemplate(sf).getMapper(PayOrderMapper.class);JdbcTemplate jdbc=new JdbcTemplate(ds);
  jdbc.update("INSERT INTO pay_order(tenant_id,app_id,user_id,user_type,merchant_order_id,subject,price,status,closing,expire_time,create_time,update_time) VALUES(72000001,1,72000001001,1,?,'Synthetic registration fixture',1001,30,b'1','2030-01-01',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE merchant_order_id=merchant_order_id",order);
  // Repair only the two fixtures created by the initial red test with missing audit timestamps.
  jdbc.update("UPDATE pay_order SET create_time=CURRENT_TIMESTAMP(6),update_time=CURRENT_TIMESTAMP(6) WHERE tenant_id=72000001 AND app_id=1 AND merchant_order_id=? AND subject='Synthetic registration fixture' AND YEAR(create_time)=0",order);
  PayOrderDO row=new PayOrderDO();row.setTenantId(72000001L);row.setAppId(1L);row.setUserId(72000001001L);row.setUserType(1);row.setMerchantOrderId(order);row.setSubject("Synthetic registration fixture");row.setPrice(1001L);row.setExpireTime(LocalDateTime.of(2030,1,1,0,0));row.setCreateTime(LocalDateTime.now());row.setUpdateTime(row.getCreateTime());
  CyclicBarrier start=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
  Callable<Void> work=()->{TenantContextHolder.setTenantId(72000001L);try{new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
   tx.setRollbackOnly();try{start.await(3,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
   int inserted=close?mapper.ensureClosed(row):mapper.ensure(row);assertThat(inserted).isIn(0,1);
   try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
   PayOrderDO locked=mapper.lock(72000001L,1L,order);assertThat(locked.getStatus()).isEqualTo(30);assertThat(locked.getClosing()).isTrue();assertThat(locked.getPrice()).isEqualTo(1001L);
  });return null;}finally{TenantContextHolder.clear();}};
  try{Future<Void>a=pool.submit(work),b=pool.submit(work);a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  TenantContextHolder.setTenantId(72000001L);
  try {new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
   tx.setRollbackOnly();row.setUserId(72000001002L);row.setPrice(1L);row.setSubject("Changed incoming request");
   if(close)mapper.ensureClosed(row);else mapper.ensure(row);
   PayOrderDO original=mapper.lock(72000001L,1L,order);assertThat(original.getUserId()).isEqualTo(72000001001L);assertThat(original.getPrice()).isEqualTo(1001L);assertThat(original.getSubject()).isEqualTo("Synthetic registration fixture");assertThat(original.getStatus()).isEqualTo(30);assertThat(original.getClosing()).isTrue();
  });}finally{TenantContextHolder.clear();}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pay_order WHERE tenant_id=72000001 AND app_id=1 AND merchant_order_id=? AND status=30 AND price=1001 AND user_id=72000001001",Integer.class,order)).isEqualTo(1);
 }
}
