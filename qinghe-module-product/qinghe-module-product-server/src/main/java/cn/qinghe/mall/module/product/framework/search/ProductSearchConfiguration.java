package cn.qinghe.mall.module.product.framework.search;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Locale;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(ProductSearchProperties.class)
@ConditionalOnProperty(prefix="qinghe.product.search",name="enabled",havingValue="true")
public class ProductSearchConfiguration {
    @Bean(destroyMethod="close") public ProductSearchIndex productSearchIndex(ProductSearchProperties p,ObjectMapper json) {
        URI uri=URI.create(p.getEndpoint());
        if(!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))throw new IllegalArgumentException("ES地址必须为固定HTTP(S)源");
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        if("http".equals(uri.getScheme()) && !("127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host) || "::1".equals(host)))throw new IllegalArgumentException("远程ES必须使用HTTPS");
        if(p.getIndex()==null || !p.getIndex().matches("qinghe-product-[a-z0-9][a-z0-9-]{0,63}"))throw new IllegalArgumentException("ES索引名不正确");
        if(p.getApiKey()==null || !p.getApiKey().isEmpty() && (!p.getApiKey().matches("[A-Za-z0-9+/=_-]{1,4096}") || !"https".equals(uri.getScheme())))throw new IllegalArgumentException("ES API凭据必须通过HTTPS传递");
        org.elasticsearch.client.RestClientBuilder builder=RestClient.builder(HttpHost.create(uri.toString()))
                .setRequestConfigCallback(c->c.setConnectTimeout(1000).setSocketTimeout(5000).setConnectionRequestTimeout(500))
                .setHttpClientConfigCallback(c->c.disableAuthCaching().setMaxConnTotal(8).setMaxConnPerRoute(8).setRedirectStrategy(new org.apache.http.impl.client.DefaultRedirectStrategy(){
                    @Override public boolean isRedirected(org.apache.http.HttpRequest request,org.apache.http.HttpResponse response,org.apache.http.protocol.HttpContext context){return false;}
                }));
        if(!p.getApiKey().isEmpty())builder.setDefaultHeaders(new org.apache.http.Header[]{new BasicHeader("Authorization","ApiKey "+p.getApiKey())});
        return new ProductSearchIndex(builder.build(),p.getIndex(),json);
    }
}
