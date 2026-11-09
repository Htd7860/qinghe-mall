package cn.qinghe.mall.module.product.framework.search;
import cn.qinghe.mall.module.product.service.search.EsProduct;
import cn.qinghe.mall.module.product.service.search.ProductSearchDocument;
import cn.qinghe.mall.module.product.service.search.ProductSearchQuery;
import cn.qinghe.mall.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import com.fasterxml.jackson.databind.*;
import org.elasticsearch.client.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;

/** ES专用固定写入适配；后续持久同步调用，不创建/删除索引、不接公开HTTP。
 * Versioned write adaptation of macro mall create/delete duties; Apache-2.0 attribution retained.
 */
public final class ProductSearchIndex implements Closeable {
    public enum WriteResult { APPLIED, DUPLICATE, SUPERSEDED }
    private final RestClient client;private final String index;private final ObjectMapper json;
    private final Semaphore calls=new Semaphore(8);
    private final RequestOptions options=boundedOptions();
    private static RequestOptions boundedOptions() {
        RequestOptions.Builder builder=RequestOptions.DEFAULT.toBuilder();
        builder.setHttpAsyncResponseConsumerFactory(new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(65536));
        return builder.build();
    }
    ProductSearchIndex(RestClient client,String index,ObjectMapper json){this.client=client;this.index=index;this.json=json;}
    public WriteResult put(EsProduct product) {
        return putSnapshot(ProductSearchDocument.of(json,product));
    }
    /** 发送数据库已保存的不可变正文，不按当前商品状态重建旧版本。 */
    public WriteResult putSnapshot(ProductSearchDocument product) {
        return putSnapshot(product,null);
    }
    public WriteResult putSnapshot(ProductSearchDocument product,Target expected) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("ES写入必须在数据库事务外");
        if(product==null)throw new IllegalArgumentException("商品搜索快照缺失");
        if(expected!=null && !index.equals(expected.alias))throw new IllegalArgumentException("商品搜索目标别名不一致");
        if(!calls.tryAcquire())throw new IllegalStateException("ES写入并发已满");
        try {
            String hash=product.getHash();
            String path="/"+index+"/_doc/"+product.getTenantId()+"-"+product.getId();
            Request request=new Request("PUT",path);request.addParameter("routing",product.getTenantId().toString());
            request.addParameter("version",product.getVersion().toString());request.addParameter("version_type","external");
            // Provisioned alias only: never silently auto-create an index with the wrong mapping.
            request.addParameter("require_alias","true");
            // Refresh before DB ACK in the later synchronizer; query readiness remains a separate DB barrier.
            request.addParameter("refresh","wait_for");request.addParameter("timeout","3s");request.setJsonEntity(product.getPayload());request.setOptions(options);
            try {
                JsonNode response=read(client.performRequest(request));
                if(expected!=null && !expected.physical.equals(response.path("_index").asText())
                        || number(response.path("_version"))!=product.getVersion()
                        || !response.path("_id").asText().equals(product.getTenantId()+"-"+product.getId())
                        || !response.path("_shards").path("failed").isIntegralNumber()
                        || !response.path("_shards").path("failed").canConvertToInt()
                        || response.path("_shards").path("failed").intValue()!=0)throw new IOException("ES写入回执不完整");
                return WriteResult.APPLIED;
            } catch(ResponseException conflict) {
                if(conflict.getResponse().getStatusLine().getStatusCode()!=409)throw new IOException("ES写入未确认");
                Request get=new Request("GET",path);get.addParameter("routing",product.getTenantId().toString());get.setOptions(options);
                JsonNode current=read(client.performRequest(get)), source=current.path("_source");
                if(expected!=null && !expected.physical.equals(current.path("_index").asText())
                        || !current.path("found").isBoolean() || !current.path("found").booleanValue()
                        || number(source.path("tenantId"))!=product.getTenantId()
                        || number(source.path("id"))!=product.getId()
                        || number(source.path("version"))!=number(current.path("_version")))throw new IOException("ES版本回执不完整");
                long version=number(current.path("_version"));
                if(version>product.getVersion())return WriteResult.SUPERSEDED;
                if(version==product.getVersion() && source.path("payloadHash").isTextual()
                        && hash.equals(source.path("payloadHash").textValue()))return WriteResult.DUPLICATE;
                throw new IOException("同版本索引内容不一致");
            }
        } catch(IOException ex){throw new IllegalStateException("商品索引写入结果未确认，请保留原版本重试");}
        finally {calls.release();}
    }
    private JsonNode read(Response response)throws IOException {
        if(response.getEntity()==null)throw new IOException("ES响应为空");
        try(InputStream in=response.getEntity().getContent()){JsonNode node=json.readTree(in);if(node==null || !node.isObject())throw new IOException("ES响应不完整");return node;}
    }
    /** 显式任务调用时才检查；不在构造/启动期访问ES。仅接受单索引、无过滤/自定义路由的别名。 */
    public Target target() {
        outside();if(!calls.tryAcquire())throw new IllegalStateException("ES操作并发已满");
        try {return inspect();}
        catch(IOException ex){throw new IllegalStateException("商品搜索索引未就绪或身份未确认");}
        finally {calls.release();}
    }
    /** 固定DSL，仅取本租户原同步版本的商品引用；销售资料必须由后续MySQL权威读回。 */
    public SearchPage search(Target expected,Long tenant,Long version,AppProductSpuPageReqVO query,javax.validation.Validator validator) {
        outside();
        if(expected==null || !index.equals(expected.alias) || version==null || version<=0 || version>9007199254740991L)
            throw new IllegalArgumentException("搜索目标或版本不正确");
        com.fasterxml.jackson.databind.node.ObjectNode body=ProductSearchQuery.build(json,validator,tenant,query);
        ((com.fasterxml.jackson.databind.node.ArrayNode)body.path("query").path("bool").path("filter"))
                .addObject().putObject("term").put("version",version);
        if(!calls.tryAcquire())throw new IllegalStateException("搜索请求繁忙");
        try {
            if(!expected.equals(inspect()))throw new IOException();
            Request request=new Request("POST","/"+expected.physical+"/_search");request.addParameter("routing",tenant.toString());
            request.addParameter("allow_partial_search_results","false");request.addParameter("ignore_unavailable","false");
            request.addParameter("allow_no_indices","false");request.setOptions(options);request.setJsonEntity(body.toString());
            JsonNode response=read(client.performRequest(request));shards(response);
            if(!response.path("timed_out").isBoolean() || response.path("timed_out").booleanValue()
                    || response.has("terminated_early") && (!response.path("terminated_early").isBoolean() || response.path("terminated_early").booleanValue()))throw new IOException();
            JsonNode hits=response.path("hits"),total=hits.path("total"),rows=hits.path("hits");
            if(!"eq".equals(total.path("relation").asText()) || !total.path("value").isIntegralNumber() || !total.path("value").canConvertToLong()
                    || total.path("value").longValue()<0 || total.path("value").longValue()>Integer.MAX_VALUE || !rows.isArray())throw new IOException();
            long count=total.path("value").longValue(),offset=body.path("from").longValue();
            if(rows.size()!=Math.min(query.getPageSize(),Math.max(0L,count-offset)))throw new IOException();
            List<Long> ids=new ArrayList<>();Set<Long> seen=new HashSet<>();
            for(JsonNode hit:rows) {
                JsonNode source=hit.path("_source");
                if(!source.isObject() || source.size()!=3 || number(source.path("tenantId"))!=tenant || number(source.path("version"))!=version
                        || !expected.physical.equals(hit.path("_index").asText()))throw new IOException();
                long id=number(source.path("id"));
                if(!seen.add(id) || !(tenant+"-"+id).equals(hit.path("_id").asText()))throw new IOException();ids.add(id);
            }
            if(!expected.equals(inspect()))throw new IOException();
            return new SearchPage(count,Collections.unmodifiableList(ids));
        } catch(IOException ex){throw new IllegalStateException("商品搜索结果尚未确认");}
        finally {calls.release();}
    }
    private long number(JsonNode value)throws IOException {
        if(!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue()<=0 || value.longValue()>9007199254740991L)throw new IOException();
        return value.longValue();
    }
    public static final class SearchPage {
        private final long total;private final List<Long> ids;
        private SearchPage(long total,List<Long> ids){this.total=total;this.ids=ids;}
        public long getTotal(){return total;}
        public List<Long> getIds(){return ids;}
    }
    /** 包括同版重试在内，必须显式刷新和核对全租户/本版本数量，不能只凭写入409推进ready。 */
    public void confirm(Target expected,Long tenant,Long version,long count) {
        outside();
        if(expected==null || tenant==null || tenant<=0 || tenant>9007199254740991L || version==null || version<=0
                || version>9007199254740991L || count<0 || count>9007199254740991L)throw new IllegalArgumentException("搜索确认身份或数量不正确");
        if(!calls.tryAcquire())throw new IllegalStateException("ES操作并发已满");
        try {
            if(!expected.equals(inspect()))throw new IOException();
            Request refresh=new Request("POST","/"+expected.physical+"/_refresh");
            refresh.addParameter("ignore_unavailable","false");refresh.addParameter("allow_no_indices","false");refresh.setOptions(options);
            shards(read(client.performRequest(refresh)));
            if(count(expected,tenant,null)!=count || count(expected,tenant,version)!=count || !expected.equals(inspect()))throw new IOException();
        } catch(IOException ex){throw new IllegalStateException("商品搜索刷新、数量或索引身份尚未确认");}
        finally {calls.release();}
    }
    private long count(Target target,Long tenant,Long version)throws IOException {
        com.fasterxml.jackson.databind.node.ObjectNode body=json.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode filters=body.putObject("query").putObject("bool").putArray("filter");
        filters.addObject().putObject("term").put("tenantId",tenant);
        if(version!=null)filters.addObject().putObject("term").put("version",version);
        Request request=new Request("GET","/"+target.physical+"/_count");request.addParameter("routing",tenant.toString());
        request.setJsonEntity(body.toString());request.setOptions(options);JsonNode response=read(client.performRequest(request));shards(response);
        if(!response.path("count").isIntegralNumber() || !response.path("count").canConvertToLong()
                || response.path("count").longValue()<0 || response.path("count").longValue()>9007199254740991L)throw new IOException();
        return response.path("count").longValue();
    }
    private void shards(JsonNode response)throws IOException {
        JsonNode shards=response.path("_shards");
        if(!shards.path("total").isIntegralNumber() || !shards.path("total").canConvertToInt() || shards.path("total").intValue()<1
                || !shards.path("failed").isIntegralNumber() || !shards.path("successful").isIntegralNumber()
                || !shards.path("failed").canConvertToInt() || !shards.path("successful").canConvertToInt()
                || shards.path("failed").asInt(-1)!=0 || shards.path("successful").asInt(-1)!=shards.path("total").intValue())throw new IOException();
    }
    private Target inspect()throws IOException {
        Request alias=new Request("GET","/_alias/"+index);alias.setOptions(options);JsonNode aliases=read(client.performRequest(alias));
        if(aliases.size()!=1)throw new IOException();String physical=aliases.fieldNames().next();
        if(physical.equals(index) || !physical.matches("qinghe-product-[a-z0-9][a-z0-9-]{0,111}"))throw new IOException();
        JsonNode config=aliases.path(physical).path("aliases").path(index);
        if(!config.isObject() || config.size()>1 || config.size()==1 && (!config.has("is_write_index") || !config.path("is_write_index").isBoolean()
                || !config.path("is_write_index").booleanValue()))throw new IOException();
        Request settings=new Request("GET","/"+physical+"/_settings/index.uuid");settings.setOptions(options);
        JsonNode metadata=read(client.performRequest(settings));String uuid=metadata.path(physical).path("settings").path("index").path("uuid").asText();
        if(metadata.size()!=1 || !uuid.matches("[A-Za-z0-9_-]{1,64}"))throw new IOException();
        Request mapping=new Request("GET","/"+physical+"/_mapping");mapping.setOptions(options);JsonNode actual=read(client.performRequest(mapping)).path(physical).path("mappings");
        JsonNode required;
        try(InputStream in=ProductSearchIndex.class.getResourceAsStream("/search/product-index-v1.json")) {
            if(in==null)throw new IOException();required=json.readTree(in).path("mappings");
        }
        if(!"strict".equals(actual.path("dynamic").asText()) || !actual.path("_routing").path("required").asBoolean(false)
                || !required.path("_meta").equals(actual.path("_meta")) || !required.path("properties").equals(actual.path("properties")))throw new IOException();
        return new Target(index,physical,uuid);
    }
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("ES操作必须在数据库事务外");}
    public static final class Target {
        private final String alias,physical,uuid;
        private Target(String alias,String physical,String uuid){this.alias=alias;this.physical=physical;this.uuid=uuid;}
        public String getAlias(){return alias;}
        public String getPhysical(){return physical;}
        public String getUuid(){return uuid;}
        @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof Target))return false;Target t=(Target)o;return alias.equals(t.alias)&&physical.equals(t.physical)&&uuid.equals(t.uuid);}
        @Override public int hashCode(){return Objects.hash(alias,physical,uuid);}
    }
    @Override public void close()throws IOException{client.close();}
}
