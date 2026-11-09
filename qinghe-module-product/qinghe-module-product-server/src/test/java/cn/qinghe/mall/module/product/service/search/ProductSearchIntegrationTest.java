package cn.qinghe.mall.module.product.service.search;

import cn.qinghe.mall.framework.tenant.config.TenantProperties;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.qinghe.mall.module.product.dal.mysql.search.*;
import cn.qinghe.mall.module.product.dal.mysql.spu.AppProductSpuMapper;
import cn.qinghe.mall.module.product.framework.search.*;
import cn.qinghe.mall.module.product.framework.sku.ProductSkuProperties;
import cn.qinghe.mall.module.product.framework.storefront.ProductStorefrontAccess;
import cn.qinghe.mall.module.product.service.spu.AppProductSpuService;
import cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.*;
import org.apache.http.HttpHost;
import org.elasticsearch.client.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import javax.validation.Validation;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** DEC-074: real isolated MySQL/MP/Spring transactions and real ES; only storefront identity boundary is mocked.
 * No business application/XXL/Broker is started; fixtures and unique test indexes are retained, never truncated/deleted.
 */
@EnabledIfEnvironmentVariable(named="QINGHE_SEARCH_TEST_ENABLED",matches="true")
class ProductSearchIntegrationTest {
    private static final AtomicLong TENANTS=new AtomicLong(50_000_000L+System.currentTimeMillis()%10_000_000L);
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static ObjectMapper json=new ObjectMapper();
    private AnnotationConfigApplicationContext context;
    private RestClient es;
    private ProductSearchIndex index;
    private ProductSearchSynchronizer sync;
    private ProductSearchSnapshots snapshots;
    private ProductSearchChanges changes;
    private ProductSearchRoundMapper rounds;
    private TransactionTemplate write;
    private long tenant,base;
    private String alias;

    @BeforeAll static void databaseGuard() throws Exception {
        String url=System.getenv("QINGHE_SEARCH_TEST_URL");
        if(url==null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/qinghe_product_search_test\\?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"))
            throw new IllegalStateException("Only the explicit local dedicated product search test database is allowed");
        if(!"http://127.0.0.1:19200".equals(System.getenv("QINGHE_SEARCH_TEST_ES")))throw new IllegalStateException("Only dedicated loopback ES19200 is allowed");
        dataSource=new DriverManagerDataSource(url,System.getenv().getOrDefault("QINGHE_DB_USERNAME","root"),required("QINGHE_DB_PASSWORD"));
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        try(java.sql.Connection c=dataSource.getConnection()) {
            if(!"qinghe_product_search_test".equals(c.getCatalog()))throw new IllegalStateException("Actual database guard failed");
        }
        jdbc=new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",Integer.class)).isEqualTo(8);
    }
    private static String required(String name){String v=System.getenv(name);if(v==null || v.isEmpty())throw new IllegalStateException("Missing test environment: "+name);return v;}

    @BeforeEach void fixture() throws Exception {
        tenant=TENANTS.incrementAndGet();base=tenant*1000;alias="qinghe-product-verify-"+UUID.randomUUID().toString();
        es=RestClient.builder(new HttpHost("127.0.0.1",19200,"http")).setRequestConfigCallback(c->c.setConnectTimeout(1000).setSocketTimeout(5000)).build();
        provision(alias);
        context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("search-verification-only",Collections.singletonMap("qinghe.product.search.enabled",true)));
        context.register(Transactions.class);
        context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Validator.class,()->Validation.buildDefaultValidatorFactory().getValidator());
        context.registerBean(PlatformTransactionManager.class,()->new DataSourceTransactionManager(dataSource));
        context.registerBean(SqlSessionTemplate.class,()->{
            try {
                MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);
                MybatisPlusInterceptor plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
                MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(dataSource);factory.setConfiguration(cfg);factory.setPlugins(plugin);
                GlobalConfig global=new GlobalConfig();global.setBanner(false);global.setDbConfig(new GlobalConfig.DbConfig());factory.setGlobalConfig(global);
                org.apache.ibatis.session.SqlSessionFactory sf=factory.getObject();
                for(Class<?> type:Arrays.asList(ProductSearchControlMapper.class,ProductSearchSnapshotMapper.class,ProductSearchRoundMapper.class,AppProductSpuMapper.class))sf.getConfiguration().addMapper(type);
                return new SqlSessionTemplate(sf);
            }catch(Exception e){throw new IllegalStateException(e);}
        });
        context.registerBean(ProductSearchControlMapper.class,()->context.getBean(SqlSessionTemplate.class).getMapper(ProductSearchControlMapper.class));
        context.registerBean(ProductSearchSnapshotMapper.class,()->context.getBean(SqlSessionTemplate.class).getMapper(ProductSearchSnapshotMapper.class));
        context.registerBean(ProductSearchRoundMapper.class,()->context.getBean(SqlSessionTemplate.class).getMapper(ProductSearchRoundMapper.class));
        context.registerBean(ProductSearchChanges.class,()->new ProductSearchChanges(context.getBean(ProductSearchControlMapper.class)));
        context.registerBean(ProductSearchSnapshots.class,()->new ProductSearchSnapshots(context.getBean(ProductSearchChanges.class),context.getBean(ProductSearchSnapshotMapper.class),json));
        context.registerBean(ProductSearchIndex.class,()->{
            ProductSearchProperties p=new ProductSearchProperties();p.setEndpoint("http://127.0.0.1:19200");p.setIndex(alias);
            return new ProductSearchConfiguration().productSearchIndex(p,json);
        });
        context.registerBean(ProductSearchSynchronizer.class,()->new ProductSearchSynchronizer(context.getBean(ProductSearchControlMapper.class),context.getBean(ProductSearchRoundMapper.class),context.getBean(ProductSearchChanges.class),context.getBean(ProductSearchSnapshots.class),context.getBean(ProductSearchIndex.class),context.getBean(PlatformTransactionManager.class)));
        context.refresh();index=context.getBean(ProductSearchIndex.class);sync=context.getBean(ProductSearchSynchronizer.class);snapshots=context.getBean(ProductSearchSnapshots.class);changes=context.getBean(ProductSearchChanges.class);rounds=context.getBean(ProductSearchRoundMapper.class);
        write=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        TenantContextHolder.setTenantId(tenant);TenantContextHolder.setIgnore(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_spu WHERE tenant_id=?",Integer.class,tenant)).isZero();
        jdbc.update("INSERT INTO product_brand(id,tenant_id,name) VALUES(?,?,?)",base+1,tenant,"Verification brand");
        jdbc.update("INSERT INTO product_category(id,tenant_id,name,pic_url) VALUES(?,?,?,?)",base+2,tenant,"Verification category","/verify/category.png");
    }
    @AfterEach void close() throws Exception {TenantContextHolder.clear();if(context!=null)context.close();if(es!=null)es.close();}
    @Configuration @EnableTransactionManagement static class Transactions {}
    private void provision(String name)throws Exception {
        if(!name.matches("qinghe-product-verify-[a-z0-9-]+"))throw new IllegalStateException("Index fixture name guard");
        JsonNode mapping;
        try(InputStream in=getClass().getResourceAsStream("/search/product-index-v1.json")){mapping=json.readTree(in);}
        ((com.fasterxml.jackson.databind.node.ObjectNode)mapping).putObject("aliases").putObject(name).put("is_write_index",true);
        Request r=new Request("PUT","/"+name+"-data");r.setJsonEntity(mapping.toString());es.performRequest(r);
    }
    private void products(int count) {
        write.executeWithoutResult(tx->{
            for(int i=1;i<=count;i++) {
                long id=base+100+i;
                jdbc.update("INSERT INTO product_spu(id,tenant_id,name,description,category_id,brand_id,pic_url,slider_pic_urls,display_properties,status,price,market_price,stock,delivery_template_id,owner_user_id) VALUES(?,?,?,?,?,?,?,'[\"/verify/product.png\"]','[]',1,100,200,10,1,1)",id,tenant,"verification shirt "+i,"fixture",base+2,base+1,"/verify/product.png");
                jdbc.update("INSERT INTO product_sku(id,tenant_id,spu_id,properties,price,market_price,stock,property_key) VALUES(?,?,?,'[]',100,200,10,?)",id,tenant,id,String.join("",Collections.nCopies(64,"a")));
            }
            changes.mark(tenant);
        });
    }
    private long version(){return jdbc.queryForObject("SELECT change_version FROM product_search_control WHERE tenant_id=?",Long.class,tenant);}
    private void finish(){for(int i=0;i<100;i++){ProductSearchSynchronizer.Step step=sync.synchronize();if(step==ProductSearchSynchronizer.Step.CONFIRMED || step==ProductSearchSynchronizer.Step.CURRENT)return;}throw new AssertionError("Synchronization did not converge");}
    private ProductSearchIndex.SearchPage search(String keyword) {
        AppProductSpuPageReqVO q=new AppProductSpuPageReqVO();q.setPageNo(1);q.setPageSize(20);q.setKeyword(keyword);
        return index.search(index.target(),tenant,version(),q,context.getBean(Validator.class));
    }
    private EsProduct product(long id,long v,boolean visible) {
        EsProduct p=new EsProduct();p.setTenantId(tenant);p.setId(id);p.setVersion(v);p.setVisible(visible);
        if(visible){p.setName("verification shirt");p.setIntroduction("");p.setKeywords("shirt");p.setBrandId(base+1);p.setBrandName("Verification brand");p.setCategoryId(base+2);p.setCategoryIds(Collections.singletonList(base+2));p.setCategoryName("Verification category");p.setPicUrl("/verify/product.png");p.setPrice(100);p.setMarketPrice(200);p.setStock(10);p.setSalesCount(0);p.setSort(0);p.setCreatedAt(1700000000000L);}return p;
    }

    @Test void realDuplicateOutOfOrderAndTombstoneNeverRevive() {
        EsProduct p=product(base+900,2,true);
        assertThat(index.put(p)).isEqualTo(ProductSearchIndex.WriteResult.APPLIED);
        assertThat(index.put(p)).isEqualTo(ProductSearchIndex.WriteResult.DUPLICATE);
        p.setName("different same version");assertThatThrownBy(()->index.put(p)).isInstanceOf(IllegalStateException.class);
        assertThat(index.put(product(base+900,1,true))).isEqualTo(ProductSearchIndex.WriteResult.SUPERSEDED);
        assertThat(index.put(product(base+900,3,false))).isEqualTo(ProductSearchIndex.WriteResult.APPLIED);
        assertThat(index.put(product(base+900,2,true))).isEqualTo(ProductSearchIndex.WriteResult.SUPERSEDED);
        assertThatCode(()->index.confirm(index.target(),tenant,3L,1L)).doesNotThrowAnyException();
    }
    @Test void realMultiPageCaptureAndSendRequireRefreshBeforeReady() {
        products(23);
        assertThat(sync.synchronize()).isEqualTo(ProductSearchSynchronizer.Step.CAPTURED);
        assertThat(rounds.ready(tenant,alias,alias+"-data",index.target().getUuid())).isNull();
        assertThat(sync.synchronize()).isEqualTo(ProductSearchSynchronizer.Step.CAPTURED);
        assertThat(jdbc.queryForObject("SELECT captured_count FROM product_search_round WHERE tenant_id=?",Long.class,tenant)).isEqualTo(23L);
        finish();assertThat(search("shirt").getTotal()).isEqualTo(23);assertThat(search("shirt").getIds()).hasSize(20);
        assertThat(sync.synchronize()).isEqualTo(ProductSearchSynchronizer.Step.CURRENT);
    }
    @Test void realDatabaseRollbackRevertsProductAndChangeMarker() {
        products(1);long before=version();
        assertThatThrownBy(()->write.executeWithoutResult(tx->{jdbc.update("UPDATE product_spu SET name='must rollback' WHERE tenant_id=?",tenant);changes.mark(tenant);throw new IllegalStateException("intentional rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(version()).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT name FROM product_spu WHERE tenant_id=?",String.class,tenant)).isEqualTo("verification shirt 1");
    }
    @Test void realCapturedVersionIsImmutableAndNewDirectoryRestarts() {
        products(1);long old=version();snapshots.capture(old,0L);
        String payload=jdbc.queryForObject("SELECT payload FROM product_search_snapshot WHERE tenant_id=? AND version=?",String.class,tenant,old);
        write.executeWithoutResult(tx->{jdbc.update("UPDATE product_spu SET name='new shirt' WHERE tenant_id=?",tenant);changes.mark(tenant);});
        assertThatThrownBy(()->snapshots.capture(old,0L)).isInstanceOf(IllegalStateException.class);
        assertThat(snapshots.saved(old,0L).getDocuments().get(0).getPayload()).isEqualTo(payload);
        finish();assertThat(search("new").getTotal()).isEqualTo(1);
    }
    @Test void realDownlistingMakesReadyUnavailableThenSearchRemovesItem() {
        products(1);finish();assertThat(search("shirt").getTotal()).isEqualTo(1);
        ProductSearchIndex.Target target=index.target();
        write.executeWithoutResult(tx->{jdbc.update("UPDATE product_spu SET status=0 WHERE tenant_id=?",tenant);changes.mark(tenant);});
        assertThat(rounds.ready(tenant,target.getAlias(),target.getPhysical(),target.getUuid())).isNull();
        finish();assertThat(search("shirt").getTotal()).isZero();
    }
    @Test void publicSearchUsesDatabaseSalesRowsAndRejectsUnconfirmedVersion() {
        products(2);finish();
        ProductStorefrontAccess access=mock(ProductStorefrontAccess.class);
        javax.servlet.http.HttpServletRequest request=mock(javax.servlet.http.HttpServletRequest.class);
        MemberStorefrontRespDTO entry=new MemberStorefrontRespDTO();entry.setTenantId(tenant);entry.setCanPurchase(true);
        when(access.resolve(request,"verify")).thenReturn(entry);
        PlatformTransactionManager manager=context.getBean(PlatformTransactionManager.class);
        AppProductSpuService products=new AppProductSpuService(access,context.getBean(SqlSessionTemplate.class).getMapper(AppProductSpuMapper.class),context.getBean(Validator.class),new ProductSkuProperties(),manager);
        AppProductSearchService app=new AppProductSearchService(context.getBeanProvider(ProductSearchIndex.class),rounds,access,products,context.getBean(Validator.class),manager);
        AppProductSpuPageReqVO query=new AppProductSpuPageReqVO();query.setPageNo(1);query.setPageSize(20);query.setKeyword("shirt");
        assertThat(app.page(request,"verify",query).getList()).extracting("id").containsExactly(base+102,base+101);
        write.executeWithoutResult(tx->{
            jdbc.update("UPDATE product_spu SET price=150,stock=3 WHERE tenant_id=? AND id=?",tenant,base+101);
            jdbc.update("UPDATE product_sku SET price=150,stock=3 WHERE tenant_id=? AND spu_id=?",tenant,base+101);changes.mark(tenant);
        });
        assertThatThrownBy(()->app.page(request,"verify",query)).hasMessageContaining("更新中");
        finish();query.setMinPrice(140);query.setSortField("price");query.setSortAsc(true);
        cn.qinghe.mall.framework.common.pojo.PageResult<cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductSpuRespVO> result=app.page(request,"verify",query);
        assertThat(result.getTotal()).isEqualTo(1);assertThat(result.getList().get(0).getPrice()).isEqualTo(150);assertThat(result.getList().get(0).getStock()).isEqualTo(3);
    }
    @Test void realOtherTenantDoesNotChangeHitCount() {
        products(1);finish();long own=tenant;
        tenant=TENANTS.incrementAndGet();TenantContextHolder.setTenantId(tenant);
        EsProduct foreign=product(base+901,versionFor(own),true);index.put(foreign);
        tenant=own;TenantContextHolder.setTenantId(tenant);
        assertThat(search("shirt").getTotal()).isEqualTo(1);assertThat(search("shirt").getIds()).containsExactly(base+101);
    }
    private long versionFor(long t){return jdbc.queryForObject("SELECT change_version FROM product_search_control WHERE tenant_id=?",Long.class,t);}
    @Test void actualMapperRejectsCrossTenantReadDespiteExplicitForeignParameter() {
        products(1);long owner=tenant;TenantContextHolder.setTenantId(TENANTS.incrementAndGet());
        assertThat(context.getBean(ProductSearchSnapshotMapper.class).ids(owner,0L)).isEmpty();
    }
    @Test void realOfflineRebuildPreservesOldIndexAndRejectsOldExpectedVersion() throws Exception {
        products(1);finish();ProductSearchIndex.Target old=index.target();long oldVersion=version();
        String next="qinghe-product-verify-"+UUID.randomUUID();provision(next);
        ProductSearchProperties p=new ProductSearchProperties();p.setIndex(next);p.setEndpoint("http://127.0.0.1:19200");
        try(ProductSearchIndex newIndex=new ProductSearchConfiguration().productSearchIndex(p,json)) {
            ProductSearchSynchronizer replacement=new ProductSearchSynchronizer(context.getBean(ProductSearchControlMapper.class),rounds,changes,snapshots,newIndex,context.getBean(PlatformTransactionManager.class));
            assertThatThrownBy(()->replacement.prepareRebuild(oldVersion+1,old.getAlias(),old.getUuid())).isInstanceOf(IllegalStateException.class);
            assertThat(replacement.prepareRebuild(oldVersion,old.getAlias(),old.getUuid())).isTrue();
            assertThat(replacement.prepareRebuild(oldVersion,old.getAlias(),old.getUuid())).isFalse();
            assertThatThrownBy(sync::synchronize).isInstanceOf(IllegalStateException.class);
            for(int i=0;i<20;i++)if(replacement.synchronize()==ProductSearchSynchronizer.Step.CONFIRMED)break;
            assertThat(rounds.ready(tenant,next,next+"-data",newIndex.target().getUuid())).isNotNull();
            assertThat(index.target()).isEqualTo(old);
        }
    }
    @Test void realEsWriteSurvivesLocalAckFailureThenSameRoundRecovers() {
        products(1);assertThat(sync.synchronize()).isEqualTo(ProductSearchSynchronizer.Step.CAPTURED);
        java.util.concurrent.atomic.AtomicBoolean fail=new java.util.concurrent.atomic.AtomicBoolean(true);
        ProductSearchRoundMapper fault=(ProductSearchRoundMapper)java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class<?>[]{ProductSearchRoundMapper.class},(proxy,method,args)->{
                    if("save".equals(method.getName()) && "SEND".equals(((ProductSearchRoundMapper.Row)args[0]).getPhase()) && fail.getAndSet(false))
                        throw new IllegalStateException("test-only failure after real ES write, before local ACK");
                    try{return method.invoke(rounds,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
        ProductSearchSynchronizer failing=new ProductSearchSynchronizer(context.getBean(ProductSearchControlMapper.class),fault,changes,snapshots,index,context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(failing::synchronize).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT sent_count FROM product_search_round WHERE tenant_id=?",Long.class,tenant)).isZero();
        assertThat(search("shirt").getTotal()).isEqualTo(1); // Direct ES proof only; public ready still rejects.
        assertThat(jdbc.queryForObject("SELECT initialized+0 FROM product_search_control WHERE tenant_id=?",Integer.class,tenant)).isZero();
        finish();assertThat(search("shirt").getTotal()).isEqualTo(1);
    }
    @Test void realDedicatedEsStopRestartKeepsPendingCursorAndRecovers() throws Exception {
        products(1);sync.synchronize();
        String container="qinghe-search-verification-20261101-elasticsearch-1";
        Process inspect=new ProcessBuilder("docker","ps","--filter","label=cn.qinghe.purpose=search-verification",
                "--filter","name=^/"+container+"$","--format","{{.Names}}|{{.Image}}").redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[1024];int read;
        while((read=inspect.getInputStream().read(buffer))!=-1)output.write(buffer,0,read);
        if(inspect.waitFor()!=0 || !output.toString("UTF-8").trim().equals(container+"|docker.elastic.co/elasticsearch/elasticsearch:7.17.15"))
            throw new IllegalStateException("Only the exact dedicated validation container may be stopped");
        String before=jdbc.queryForObject("SELECT CONCAT(round_no,':',phase,':',capture_after,':',send_after) FROM product_search_round WHERE tenant_id=?",String.class,tenant);
        try {
            assertThat(dockerCommand("docker","stop","--time","5",container)).isZero();
            assertThatThrownBy(sync::synchronize).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT CONCAT(round_no,':',phase,':',capture_after,':',send_after) FROM product_search_round WHERE tenant_id=?",String.class,tenant)).isEqualTo(before);
        } finally {
            assertThat(dockerCommand("docker","start",container)).isZero();
            boolean ready=false;
            for(int i=0;i<60;i++)try{index.target();ready=true;break;}catch(IllegalStateException pending){Thread.sleep(1000);}
            assertThat(ready).isTrue();
        }
        finish();assertThat(search("shirt").getTotal()).isEqualTo(1);
    }
    private int dockerCommand(String... command)throws Exception {
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        // Do not let native child stdout bypass Surefire's fork protocol.
        try(InputStream output=process.getInputStream()){byte[] buffer=new byte[1024];while(output.read(buffer)!=-1){}}
        return process.waitFor();
    }
    @Test void realDirectoryChangeBetweenWorkAndAckRejectsBothCaptureAndSendProgress() {
        products(1);
        ProductSearchSnapshots captureRace=new ProductSearchSnapshots(changes,context.getBean(ProductSearchSnapshotMapper.class),json) {
            @Override public Page capture(Long version,Long after) {
                Page page=snapshots.capture(version,after);
                write.executeWithoutResult(tx->{jdbc.update("UPDATE product_spu SET name='capture changed shirt' WHERE tenant_id=?",tenant);changes.mark(tenant);});
                return page;
            }
        };
        PlatformTransactionManager manager=context.getBean(PlatformTransactionManager.class);
        ProductSearchSynchronizer capturing=new ProductSearchSynchronizer(context.getBean(ProductSearchControlMapper.class),rounds,changes,captureRace,index,manager);
        assertThat(capturing.synchronize()).isEqualTo(ProductSearchSynchronizer.Step.STALE);
        assertThat(jdbc.queryForObject("SELECT capture_after FROM product_search_round WHERE tenant_id=?",Long.class,tenant)).isZero();
        finish();
        write.executeWithoutResult(tx->{jdbc.update("UPDATE product_spu SET name='before send shirt' WHERE tenant_id=?",tenant);changes.mark(tenant);});
        assertThat(sync.synchronize()).isEqualTo(ProductSearchSynchronizer.Step.CAPTURED);
        ProductSearchSnapshots sendRace=new ProductSearchSnapshots(changes,context.getBean(ProductSearchSnapshotMapper.class),json) {
            @Override public Page saved(Long version,Long after) {
                Page page=snapshots.saved(version,after);
                write.executeWithoutResult(tx->{jdbc.update("UPDATE product_spu SET name='late updated shirt' WHERE tenant_id=?",tenant);changes.mark(tenant);});
                return page;
            }
        };
        ProductSearchSynchronizer sending=new ProductSearchSynchronizer(context.getBean(ProductSearchControlMapper.class),rounds,changes,sendRace,index,manager);
        assertThat(sending.synchronize()).isEqualTo(ProductSearchSynchronizer.Step.STALE);
        assertThat(jdbc.queryForObject("SELECT sent_count FROM product_search_round WHERE tenant_id=?",Long.class,tenant)).isZero();
        finish();assertThat(search("late").getTotal()).isEqualTo(1);
    }
}
