package cn.qinghe.mall.module.pay.verification;

import cn.qinghe.mall.module.pay.PayServerApplication;
import cn.qinghe.mall.module.pay.framework.pay.core.client.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.order.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.dto.refund.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.exception.PayClientException;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** DEC-076 test-classpath launcher only. Replaces the external vendor boundary, never persistence or authentication.
 * No Alipay SDK client is instantiated. Only explicit isolated DB/profile/ports and known synthetic channels.
 */
public final class CommercePayFixtureLauncher {
    public static void main(String[] args) {
        String db=System.getenv("QINGHE_PAY_DB_URL");
        if(db==null || !db.startsWith("jdbc:mysql://127.0.0.1:3306/qinghe_pay_commerce_test?")
                || !"commerce-verification".equals(System.getenv("SPRING_PROFILES_ACTIVE"))
                || !"28086".equals(System.getenv("PAY_PORT")))throw new IllegalStateException("Dedicated commerce pay fixture guard");
        ConfigurableApplicationContext context=SpringApplication.run(new Class<?>[]{PayServerApplication.class,FixtureConfiguration.class},args);
        if(!(context.getBean(PayClientFactory.class) instanceof FixtureFactory)) {
            context.close();throw new IllegalStateException("Test factory was not selected; refusing vendor traffic");
        }
    }
    @Configuration @Profile("commerce-verification")
    public static class FixtureConfiguration {
        @Bean @Primary public PayClientFactory commerceVerificationPayClientFactory() throws Exception {return new FixtureFactory();}
    }
    static final class Order {
        final long channel;final String no;final long price;final LocalDateTime deadline;
        int status;LocalDateTime paidAt;
        Order(long channel,PayOrderUnifiedReqDTO request){this.channel=channel;no=request.getOutTradeNo();price=request.getPrice();deadline=request.getExpireTime();}
    }
    static final class Refund {final String order,no;final long amount;final LocalDateTime at=now();Refund(PayRefundUnifiedReqDTO r){order=r.getOutTradeNo();no=r.getOutRefundNo();amount=r.getRefundPrice();}}
    static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS);}
    static final class FixtureFactory implements PayClientFactory,DisposableBean {
        final Map<String,Order> orders=new ConcurrentHashMap<>();final Map<String,Refund> refunds=new ConcurrentHashMap<>();
        final Map<String,AtomicInteger> faults=new ConcurrentHashMap<>();final ObjectMapper json=new ObjectMapper();final HttpServer control;
        final String key;
        FixtureFactory() throws Exception {
            this(System.getenv("COMMERCE_FIXTURE_CONTROL_TOKEN"),new InetSocketAddress("127.0.0.1",28089));
        }
        FixtureFactory(String controlKey,InetSocketAddress controlAddress) throws Exception {
            key=controlKey;
            if(key==null || !key.matches("[A-Za-z0-9_-]{43,128}"))throw new IllegalStateException("Missing isolated fixture controller credential");
            control=HttpServer.create(controlAddress,0);control.createContext("/",this::command);control.start();
        }
        @Override public PayClient<AlipayPayClientConfig> createAlipayQr(Long channel,AlipayPayClientConfig config) {
            if(channel==null || channel<1 || channel>2 || config==null || !Arrays.asList("2026000000000001","2026000000000002").contains(config.getAppId()))throw new IllegalStateException("Unknown synthetic channel");
            return new Client(this,channel,config);
        }
        void fault(String operation){AtomicInteger n=faults.get(operation);if(n!=null && n.getAndUpdate(v->Math.max(0,v-1))>0)throw new PayClientException();}
        synchronized Order submit(long channel,PayOrderUnifiedReqDTO r) {
            Order previous=orders.get(r.getOutTradeNo());
            if(previous!=null){if(previous.channel!=channel || previous.price!=r.getPrice() || !Objects.equals(previous.deadline,r.getExpireTime()))throw new PayClientException();return previous;}
            Order created=new Order(channel,r);orders.put(r.getOutTradeNo(),created);return created;
        }
        Order order(long channel,String no){Order o=orders.get(no);if(o==null || o.channel!=channel)throw new PayClientException();return o;}
        synchronized Refund refund(long channel,PayRefundUnifiedReqDTO r) {
            Order o=order(channel,r.getOutTradeNo());if(o.status!=10 || o.price!=r.getPayPrice())throw new PayClientException();
            Refund old=refunds.get(r.getOutRefundNo());
            if(old!=null){if(!old.order.equals(r.getOutTradeNo()) || old.amount!=r.getRefundPrice())throw new PayClientException();return old;}
            long total=refunds.values().stream().filter(v->v.order.equals(o.no)).mapToLong(v->v.amount).sum();
            if(r.getRefundPrice()<=0 || Math.addExact(total,r.getRefundPrice())>o.price)throw new PayClientException();
            Refund v=new Refund(r);refunds.put(v.no,v);return v;
        }
        void command(HttpExchange e) throws java.io.IOException {
            int status=200;Map<String,Object> result=new LinkedHashMap<>();
            try {
                String auth=e.getRequestHeaders().getFirst("Authorization");
                if(auth==null || !MessageDigest.isEqual(("Bearer "+key).getBytes(StandardCharsets.UTF_8),auth.getBytes(StandardCharsets.UTF_8))){status=403;throw new IllegalArgumentException();}
                String path=e.getRequestURI().getPath();
                if("GET".equals(e.getRequestMethod()) && "/health".equals(path)){result.put("mode","TEST_ONLY_NO_EXTERNAL_SDK");result.put("orders",orders.size());result.put("refunds",refunds.size());}
                else if("POST".equals(e.getRequestMethod()) && "/faults".equals(path)) {
                    JsonNode body=json.readTree(e.getRequestBody());String op=body.path("operation").asText();int count=body.path("count").asInt();
                    if(!Arrays.asList("submitAfterCommit","get","close","refundAfterCommit","getRefund").contains(op)||count<0||count>3)throw new IllegalArgumentException();faults.put(op,new AtomicInteger(count));result.put("configured",true);
                } else if("POST".equals(e.getRequestMethod()) && path.matches("/order/[A-Za-z0-9_-]{1,100}/paid")) {
                    String no=path.split("/")[2];Order o=orders.get(no);if(o==null)throw new IllegalArgumentException();
                    synchronized(this){if(o.status==20 || !now().isBefore(o.deadline))throw new IllegalArgumentException();o.status=10;if(o.paidAt==null)o.paidAt=now();}
                    result.put("marked",true);result.put("amount",o.price);
                } else {status=404;throw new IllegalArgumentException();}
            }catch(Exception rejected){if(status==200)status=400;result.clear();result.put("rejected",true);}
            byte[] data=json.writeValueAsBytes(result);e.getResponseHeaders().set("Content-Type","application/json");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(status,data.length);e.getResponseBody().write(data);e.close();
        }
        @Override public void destroy(){control.stop(0);}
    }
    static final class Client implements PayClient<AlipayPayClientConfig> {
        final FixtureFactory factory;final long id;final AlipayPayClientConfig config;
        Client(FixtureFactory f,long id,AlipayPayClientConfig c){factory=f;this.id=id;config=c;}
        public Long getId(){return id;}public AlipayPayClientConfig getConfig(){return config;}
        PayOrderRespDTO view(Order o){PayOrderRespDTO r=o.status==10?PayOrderRespDTO.successOf("fixture-"+o.no,"fixture-buyer",o.paidAt,o.no,null):o.status==20?PayOrderRespDTO.closedOf(null,null,o.no,null):PayOrderRespDTO.waitingOf("qr_code","https://qr.alipaydev.com/fixture/"+o.no,o.no,null);r.setPrice(o.price);return r;}
        public PayOrderRespDTO unifiedOrder(PayOrderUnifiedReqDTO r){Order o=factory.submit(id,r);factory.fault("submitAfterCommit");return view(o);}
        public PayOrderRespDTO recoverQr(PayOrderUnifiedReqDTO r){return view(factory.submit(id,r));}
        public PayOrderRespDTO getOrder(String no){factory.fault("get");return view(factory.order(id,no));}
        public PayOrderRespDTO closeOrder(String no){factory.fault("close");Order o=factory.order(id,no);synchronized(factory){if(o.status!=10)o.status=20;}return view(o);}
        public PayOrderRespDTO parseOrderNotify(Map<String,String> params,String body,Map<String,String> headers){Order o=factory.order(id,params.get("out_trade_no"));if(o.status!=10)throw new PayClientException();return view(o);}
        PayRefundRespDTO view(Refund v){PayRefundRespDTO r=PayRefundRespDTO.successOf("fixture-"+v.no,v.at,v.no,null);r.setRefundPrice(v.amount);return r;}
        public PayRefundRespDTO unifiedRefund(PayRefundUnifiedReqDTO r){Refund v=factory.refund(id,r);factory.fault("refundAfterCommit");return view(v);}
        public PayRefundRespDTO getRefund(String order,String no){factory.fault("getRefund");Refund v=factory.refunds.get(no);if(v==null||!v.order.equals(order))throw new PayClientException();return view(v);}
        public PayRefundRespDTO parseRefundNotify(Map<String,String> params,String body,Map<String,String> headers){throw new PayClientException();}
    }
}
