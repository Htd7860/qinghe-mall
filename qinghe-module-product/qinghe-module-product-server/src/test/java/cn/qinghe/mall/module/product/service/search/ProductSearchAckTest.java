package cn.qinghe.mall.module.product.service.search;
import cn.qinghe.mall.module.product.framework.search.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** DEC-074: artificial loopback protocol responses, explicitly separate from real ES integration evidence. */
class ProductSearchAckTest {
    private final ObjectMapper json=new ObjectMapper();
    private EsProduct tombstone(){EsProduct p=new EsProduct();p.setTenantId(101L);p.setId(1L);p.setVersion(1L);p.setVisible(false);return p;}
    private ProductSearchIndex client(HttpServer server) {
        ProductSearchProperties p=new ProductSearchProperties();p.setEndpoint("http://127.0.0.1:"+server.getAddress().getPort());p.setIndex("qinghe-product-verify-ack");
        return new ProductSearchConfiguration().productSearchIndex(p,json);
    }
    private HttpServer server(String body,int status)throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",x->{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);x.close();});server.start();return server;
    }
    @ParameterizedTest @ValueSource(strings={"18446744073709551617","9007199254740992","0","-1","\"1\"","1.0"})
    void malformedOrOutOfDomainVersionCannotConfirm(String version)throws Exception {
        HttpServer server=server("{\"_id\":\"101-1\",\"_version\":"+version+",\"_shards\":{\"failed\":0}}",200);
        try(ProductSearchIndex client=client(server)){assertThatThrownBy(()->client.put(tombstone())).isInstanceOf(IllegalStateException.class);}
        finally {server.stop(0);}
    }
    @Test void actualIntegralVersionConfirms()throws Exception {
        HttpServer server=server("{\"_id\":\"101-1\",\"_version\":1,\"_shards\":{\"failed\":0}}",200);
        try(ProductSearchIndex client=client(server)){assertThat(client.put(tombstone())).isEqualTo(ProductSearchIndex.WriteResult.APPLIED);}
        finally {server.stop(0);}
    }
    @Test void stringShardFailureIsNotAcceptedAsIntegerZero()throws Exception {
        HttpServer server=server("{\"_id\":\"101-1\",\"_version\":1,\"_shards\":{\"failed\":\"0\"}}",200);
        try(ProductSearchIndex client=client(server)){assertThatThrownBy(()->client.put(tombstone())).isInstanceOf(IllegalStateException.class);}
        finally {server.stop(0);}
    }
    @Test void failedWriteCanRetrySameImmutableDocument()throws Exception {
        AtomicInteger calls=new AtomicInteger();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",x->{int status=calls.incrementAndGet()==1?503:200;byte[] b="{\"_id\":\"101-1\",\"_version\":1,\"_shards\":{\"failed\":0}}".getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);x.close();});server.start();
        ProductSearchDocument original=ProductSearchDocument.of(json,tombstone());
        try(ProductSearchIndex client=client(server)) {
            assertThatThrownBy(()->client.putSnapshot(original)).isInstanceOf(IllegalStateException.class);
            assertThat(client.putSnapshot(original)).isEqualTo(ProductSearchIndex.WriteResult.APPLIED);
        }finally {server.stop(0);}
    }
    @ParameterizedTest @ValueSource(strings={"valid","timeout","partial","inexact","foreign","bad-id","wrong-version"})
    void publicSearchProtocolRejectsIncompleteOrForeignResponses(String scenario)throws Exception {
        String alias="qinghe-product-verify-ack",physical=alias+"-data";
        com.fasterxml.jackson.databind.node.ObjectNode reply=json.createObjectNode();reply.put("timed_out","timeout".equals(scenario));
        reply.putObject("_shards").put("total",1).put("successful","partial".equals(scenario)?0:1).put("failed","partial".equals(scenario)?1:0);
        com.fasterxml.jackson.databind.node.ObjectNode hits=reply.putObject("hits");hits.putObject("total").put("value",1).put("relation","inexact".equals(scenario)?"gte":"eq");
        com.fasterxml.jackson.databind.node.ObjectNode hit=hits.putArray("hits").addObject();hit.put("_index",physical).put("_id","bad-id".equals(scenario)?"101-2":"101-1");
        hit.putObject("_source").put("tenantId","foreign".equals(scenario)?202:101).put("id",1).put("version","wrong-version".equals(scenario)?2:1);
        com.fasterxml.jackson.databind.JsonNode mapping;
        try(java.io.InputStream in=getClass().getResourceAsStream("/search/product-index-v1.json")){mapping=json.readTree(in).path("mappings");}
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",x->{
            String path=x.getRequestURI().getPath();com.fasterxml.jackson.databind.node.ObjectNode body=json.createObjectNode();
            if(path.equals("/_alias/"+alias))body.putObject(physical).putObject("aliases").putObject(alias).put("is_write_index",true);
            else if(path.endsWith("/_settings/index.uuid"))body.putObject(physical).putObject("settings").putObject("index").put("uuid","verification-uuid");
            else if(path.endsWith("/_mapping"))body.putObject(physical).set("mappings",mapping);
            else body.setAll(reply);
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
        });server.start();
        try(ProductSearchIndex client=client(server);javax.validation.ValidatorFactory validation=javax.validation.Validation.buildDefaultValidatorFactory()) {
            cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductSpuPageReqVO query=new cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductSpuPageReqVO();query.setPageNo(1);query.setPageSize(20);
            ProductSearchIndex.Target target=client.target();
            if("valid".equals(scenario))assertThat(client.search(target,101L,1L,query,validation.getValidator()).getIds()).containsExactly(1L);
            else assertThatThrownBy(()->client.search(target,101L,1L,query,validation.getValidator())).isInstanceOf(IllegalStateException.class);
        }finally {server.stop(0);}
    }
}
