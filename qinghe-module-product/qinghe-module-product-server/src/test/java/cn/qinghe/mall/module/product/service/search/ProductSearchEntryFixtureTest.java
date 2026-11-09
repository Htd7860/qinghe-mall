package cn.qinghe.mall.module.product.service.search;

import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.qinghe.mall.module.product.dal.mysql.search.*;
import cn.qinghe.mall.module.product.framework.search.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** DEC-075 only: synchronize previously seeded synthetic entry fixtures with real production components.
 * No mock identity, business accounts, application startup, direct ready updates or fixture cleanup.
 */
@EnabledIfEnvironmentVariable(named="QINGHE_SEARCH_ENTRY_ENABLED",matches="true")
class ProductSearchEntryFixtureTest {
    @Configuration @EnableTransactionManagement static class Transactions {}
    @Test void synchronizeSyntheticEntryFixtures() throws Exception {
        String url="jdbc:mysql://127.0.0.1:3306/qinghe_product_search_entry_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        assertThat(System.getenv("QINGHE_SEARCH_ENTRY_DB_URL")).isEqualTo(url);
        String alias=System.getenv("QINGHE_SEARCH_ENTRY_ALIAS");
        assertThat(alias).matches("qinghe-product-entry-[0-9a-f-]{36}");
        String password=System.getenv("QINGHE_DB_PASSWORD");assertThat(password!=null && !password.isEmpty()).isTrue();
        DriverManagerDataSource ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("QINGHE_DB_USERNAME","root"),password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        try(java.sql.Connection c=ds.getConnection()){assertThat(c.getCatalog()).isEqualTo("qinghe_product_search_entry_test");}
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        assertThat(jdbc.queryForList("SELECT DISTINCT tenant_id FROM product_spu ORDER BY tenant_id",Long.class)).containsExactly(71000001L,71000002L);
        try(AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "entry-fixture-only",Collections.singletonMap("qinghe.product.search.enabled",true)));
            context.register(Transactions.class);
            context.registerBean(ObjectMapper.class,()->new ObjectMapper());
            context.registerBean(PlatformTransactionManager.class,()->new DataSourceTransactionManager(ds));
            context.registerBean(SqlSessionTemplate.class,()->{
                try {
                    MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
                    MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
                    MybatisSqlSessionFactoryBean f=new MybatisSqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(cfg);f.setPlugins(plugin);
                    GlobalConfig global=new GlobalConfig();global.setBanner(false);global.setDbConfig(new GlobalConfig.DbConfig());f.setGlobalConfig(global);
                    org.apache.ibatis.session.SqlSessionFactory sf=f.getObject();
                    for(Class<?> t:Arrays.asList(ProductSearchControlMapper.class,ProductSearchSnapshotMapper.class,ProductSearchRoundMapper.class))sf.getConfiguration().addMapper(t);
                    return new SqlSessionTemplate(sf);
                }catch(Exception ex){throw new IllegalStateException(ex);}
            });
            context.registerBean(ProductSearchControlMapper.class,()->context.getBean(SqlSessionTemplate.class).getMapper(ProductSearchControlMapper.class));
            context.registerBean(ProductSearchSnapshotMapper.class,()->context.getBean(SqlSessionTemplate.class).getMapper(ProductSearchSnapshotMapper.class));
            context.registerBean(ProductSearchRoundMapper.class,()->context.getBean(SqlSessionTemplate.class).getMapper(ProductSearchRoundMapper.class));
            context.registerBean(ProductSearchChanges.class,()->new ProductSearchChanges(context.getBean(ProductSearchControlMapper.class)));
            context.registerBean(ProductSearchSnapshots.class,()->new ProductSearchSnapshots(context.getBean(ProductSearchChanges.class),context.getBean(ProductSearchSnapshotMapper.class),context.getBean(ObjectMapper.class)));
            context.registerBean(ProductSearchIndex.class,()->{ProductSearchProperties p=new ProductSearchProperties();p.setEndpoint("http://127.0.0.1:19200");p.setIndex(alias);return new ProductSearchConfiguration().productSearchIndex(p,context.getBean(ObjectMapper.class));});
            context.registerBean(ProductSearchSynchronizer.class,()->new ProductSearchSynchronizer(context.getBean(ProductSearchControlMapper.class),context.getBean(ProductSearchRoundMapper.class),context.getBean(ProductSearchChanges.class),context.getBean(ProductSearchSnapshots.class),context.getBean(ProductSearchIndex.class),context.getBean(PlatformTransactionManager.class)));
            context.refresh();TransactionTemplate write=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            try {
                for(long tenant:new long[]{71000001L,71000002L}) {
                    TenantContextHolder.setTenantId(tenant);TenantContextHolder.setIgnore(false);
                    if("true".equals(System.getenv("QINGHE_SEARCH_ENTRY_MUTATE")) && tenant==71000001L) {
                        write.executeWithoutResult(tx->{jdbc.update("UPDATE product_spu SET introduction='Entry revised fixture' WHERE tenant_id=? AND id=?",tenant,tenant*1000+101);context.getBean(ProductSearchChanges.class).mark(tenant);});
                    } else if(jdbc.queryForObject("SELECT COUNT(*) FROM product_search_control WHERE tenant_id=?",Integer.class,tenant)==0) {
                        write.executeWithoutResult(tx->context.getBean(ProductSearchChanges.class).mark(tenant));
                    }
                    if("true".equals(System.getenv("QINGHE_SEARCH_ENTRY_STAGE_ONLY")))continue;
                    boolean done=false;
                    for(int i=0;i<100;i++){ProductSearchSynchronizer.Step step=context.getBean(ProductSearchSynchronizer.class).synchronize();if(step==ProductSearchSynchronizer.Step.CONFIRMED || step==ProductSearchSynchronizer.Step.CURRENT){done=true;break;}}
                    assertThat(done).as("Synthetic fixture synchronization converged").isTrue();
                }
            }finally{TenantContextHolder.clear();}
        }
    }
}
