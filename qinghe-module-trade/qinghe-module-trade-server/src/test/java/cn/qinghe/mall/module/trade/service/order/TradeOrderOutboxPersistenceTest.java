package cn.qinghe.mall.module.trade.service.order;
import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.qinghe.mall.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.qinghe.mall.module.trade.dal.mysql.order.*;
import cn.qinghe.mall.module.trade.service.message.TradeOrderEventEnvelope;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** DEC-076 exact isolated DB; real MP/JDBC order+outbox transaction, always rolled back. */
@EnabledIfEnvironmentVariable(named="QINGHE_COMMERCE_TEST_ENABLED",matches="true")
class TradeOrderOutboxPersistenceTest {
    @Test void orderAndExpiryEventAreWrittenTogetherWithExactDeadline() throws Exception {
        String url="jdbc:mysql://127.0.0.1:3306/qinghe_trade_commerce_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        DriverManagerDataSource ds=new DriverManagerDataSource(url,System.getenv().getOrDefault("QINGHE_DB_USERNAME","root"),System.getenv("QINGHE_DB_PASSWORD"));
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");try(java.sql.Connection c=ds.getConnection()){assertThat(c.getCatalog()).isEqualTo("qinghe_trade_commerce_test");}
        MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new cn.qinghe.mall.framework.tenant.core.db.MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        MybatisSqlSessionFactoryBean f=new MybatisSqlSessionFactoryBean();f.setDataSource(ds);f.setConfiguration(cfg);f.setPlugins(plugin);
        GlobalConfig global=new GlobalConfig();global.setBanner(false);global.setDbConfig(new GlobalConfig.DbConfig());f.setGlobalConfig(global);
        org.apache.ibatis.session.SqlSessionFactory sf=f.getObject();sf.getConfiguration().addMapper(TradeOrderMapper.class);sf.getConfiguration().addMapper(TradeOrderOutboxMapper.class);sf.getConfiguration().addMapper(TradeOrderFulfillmentMapper.class);
        SqlSessionTemplate session=new SqlSessionTemplate(sf);JdbcTemplate jdbc=new JdbcTemplate(ds);
        TradeOrderMapper orders=session.getMapper(TradeOrderMapper.class);TradeOrderOutboxMapper events=session.getMapper(TradeOrderOutboxMapper.class);
        long id=9_000_000_000L;assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order WHERE id=?",Integer.class,id)).isZero();
        TenantContextHolder.setTenantId(72000001L);TenantContextHolder.setIgnore(false);
        try {
            new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->{
                tx.setRollbackOnly();TradeOrderDO o=new TradeOrderDO();o.setId(id);o.setTenantId(72000001L);o.setUserId(72000001001L);
                o.setNo("QH"+UUID.randomUUID().toString().replace("-",""));o.setType(0);o.setUserRemark("");o.setProductCount(1);
                o.setTotalPrice(100L);o.setDiscountPrice(0L);o.setDeliveryPrice(0L);o.setPayPrice(100L);
                o.setCreateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS));o.setUpdateTime(o.getCreateTime());o.setPayDeadline(o.getCreateTime().plusMinutes(30));
                o.setReceiverName("Fixture");o.setReceiverMobile("13800000001");o.setReceiverAreaId(310101);o.setReceiverDetailAddress("Synthetic fixture");
                assertThat(orders.insert(o)).isEqualTo(1);assertThat(session.getMapper(TradeOrderFulfillmentMapper.class).items(o.getTenantId(),id)).isEmpty();
                TradeOrderDO stored=orders.paymentOrder(o.getTenantId(),id);
                assertThat(stored.getPayDeadline()).as("Original deadline must survive JDBC").isEqualTo(o.getPayDeadline());
                String payload="{\"dueAt\":"+TradeOrderEventEnvelope.epochCeiling(o.getPayDeadline())+"}";
                for(String invalid:new String[]{"{\"dueAt\":\"1793500000000\"}","{\"dueAt\":1.5}","{\"dueAt\":null}","{\"dueAt\":-1}","{\"dueAt\":0}","{\"dueAt\":9007199254740992}"}) {
                    assertThat(events.insertExpiry(o.getTenantId(),id,UUID.randomUUID().toString(),invalid,o.getPayDeadline(),o.getCreateTime())).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_outbox WHERE tenant_id=? AND preparation_id=?",Integer.class,o.getTenantId(),id)).isZero();
                }
                int written=events.insertExpiry(o.getTenantId(),id,UUID.randomUUID().toString(),payload,o.getPayDeadline(),o.getCreateTime());
                java.util.List<Map<String,Object>> warnings=jdbc.queryForList("SHOW WARNINGS");
                assertThat(written).as("Order expiry insert; database warnings: %s",warnings).isEqualTo(1);
                assertThat(events.next(o.getTenantId(),o.getCreateTime().plusSeconds(1))).isNotNull();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order_outbox WHERE tenant_id=? AND preparation_id=?",Integer.class,o.getTenantId(),id)).isEqualTo(1);
            });
        }finally{TenantContextHolder.clear();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade_order WHERE id=?",Integer.class,id)).isZero();
    }
}
