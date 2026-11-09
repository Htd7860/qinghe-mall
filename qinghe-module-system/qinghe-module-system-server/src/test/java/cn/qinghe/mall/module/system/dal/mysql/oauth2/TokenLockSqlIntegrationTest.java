package cn.qinghe.mall.module.system.dal.mysql.oauth2;
import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
/** DEC-076 real MySQL tenant/locking SQL, no real credentials selected or logged. */
@EnabledIfEnvironmentVariable(named="QINGHE_COMMERCE_TEST_ENABLED",matches="true")
class TokenLockSqlIntegrationTest {
 @Test void lockingQueryIsValidWithTenantAndDeterministicOrder() throws Exception {
  String url="jdbc:mysql://127.0.0.1:3306/qinghe_system_commerce_test?serverTimezone=Asia/Shanghai";
  DriverManagerDataSource ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("QINGHE_DB_USERNAME","root"),System.getenv("QINGHE_DB_PASSWORD"));
  try(java.sql.Connection c=ds.getConnection()){assertThat(c.getCatalog()).isEqualTo("qinghe_system_commerce_test");}
  MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);cfg.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
  MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new cn.qinghe.mall.framework.tenant.core.db.MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
  MybatisSqlSessionFactoryBean f=new MybatisSqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(cfg);f.setPlugins(plugin);
  GlobalConfig g=new GlobalConfig();g.setBanner(false);g.setDbConfig(new GlobalConfig.DbConfig());f.setGlobalConfig(g);
  org.apache.ibatis.session.SqlSessionFactory sf=f.getObject();sf.getConfiguration().addMapper(OAuth2AccessTokenMapper.class);
  OAuth2AccessTokenMapper mapper=new SqlSessionTemplate(sf).getMapper(OAuth2AccessTokenMapper.class);
  TenantContextHolder.setTenantId(72000001L);TenantContextHolder.setIgnore(false);
  try {new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{tx.setRollbackOnly();assertThat(mapper.selectListByRefreshTokenForUpdate("fixture-not-a-credential")).isEmpty();
   JdbcTemplate jdbc=new JdbcTemplate(ds);
   for(int i=0;i<4;i++)jdbc.update("INSERT INTO system_oauth2_access_token(access_token,refresh_token,user_id,user_type,user_info,client_id,scopes,expires_time,tenant_id,deleted) VALUES(?, 'fixture-not-a-credential',1,1,'{}','qinghe-app','[]',DATE_ADD(NOW(),INTERVAL 1 HOUR),?,?)","fixture-access-"+i,i==2?72000002L:72000001L,i==3);
   java.util.List<cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO> rows=mapper.selectListByRefreshTokenForUpdate("fixture-not-a-credential");
   assertThat(rows).hasSize(2);assertThat(rows.get(0).getId()).isLessThan(rows.get(1).getId());
   assertThat(rows).allSatisfy(row->{assertThat(row.getTenantId()).isEqualTo(72000001L);assertThat(row.getDeleted()).isFalse();assertThat(row.getScopes()).isEmpty();assertThat(row.getUserInfo()).isEmpty();});});}
  finally {TenantContextHolder.clear();}
 }
}
