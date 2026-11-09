package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.*;
import cn.qinghe.mall.module.promotion.dal.dataobject.seckill.*;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillRefundRecordMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** DEC-077: actual MySQL + production tenant parser. A private temporary projection shadows no business rows. */
@EnabledIfEnvironmentVariable(named="QINGHE_SECKILL_TEST_ENABLED",matches="true")
class SeckillRefundLimitSqlTest {
    private SingleConnectionDataSource data;
    private SeckillRefundRecordMapper mapper;
    private final String request="00000000-0000-4000-8000-000000000077";
    @BeforeEach void setup() throws Exception {
        data=new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13307/qinghe_promotion_seckill_test?serverTimezone=UTC",
                "root",System.getenv("SECKILL_MYSQL_ROOT_PASSWORD"),true);
        assertEquals("qinghe_promotion_seckill_test",data.getConnection().getCatalog());
        JdbcTemplate jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TEMPORARY TABLE promotion_seckill_refund_record(tenant_id BIGINT,request_no VARCHAR(36),refunded_count INT,restore_count INT,refund_amount BIGINT)");
        jdbc.update("INSERT INTO promotion_seckill_refund_record VALUES(73000001,?,1,1,601),(73000002,?,1000,1000,999999)",request,request);
        MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new MySqlTenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(data);factory.setConfiguration(cfg);factory.setPlugins(plugin);
        org.apache.ibatis.session.SqlSessionFactory sessions=factory.getObject();sessions.getConfiguration().addMapper(SeckillRefundRecordMapper.class);
        mapper=new SqlSessionTemplate(sessions).getMapper(SeckillRefundRecordMapper.class);
        TenantContextHolder.setTenantId(73000001L);
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();if(data!=null)data.destroy();}
    private int check(String number,int refunded,int restored,long amount) {
        SeckillRequestDO original=new SeckillRequestDO();original.setTenantId(73000001L);original.setRequestNo(number);original.setQuantity(2);original.setPayAmount(1202L);
        SeckillRefundRecordDO next=new SeckillRefundRecordDO();next.setRefundedCount(refunded);next.setRestoreCount(restored);next.setRefundAmount(amount);
        return mapper.withinOriginal(original,next);
    }
    @Test void exactBoundaryExcludesOtherTenantHistory(){assertEquals(1,check(request,1,1,601));}
    @Test void refundedCountCannotExceedOriginal(){assertEquals(0,check(request,2,1,601));}
    @Test void restoredCountCannotExceedOriginal(){assertEquals(0,check(request,1,2,601));}
    @Test void amountCannotExceedOriginal(){assertEquals(0,check(request,1,1,602));}
    @Test void emptyHistoryAllowsExactOriginal(){assertEquals(1,check("00000000-0000-4000-8000-000000000078",2,2,1202));}
    @Test void emptyHistoryStillRejectsOverRefund(){assertEquals(0,check("00000000-0000-4000-8000-000000000078",2,2,1203));}
}
