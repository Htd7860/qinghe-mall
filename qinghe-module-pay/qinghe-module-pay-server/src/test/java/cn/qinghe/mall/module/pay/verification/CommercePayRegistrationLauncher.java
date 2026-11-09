package cn.qinghe.mall.module.pay.verification;
import cn.qinghe.mall.module.pay.PayServerApplication;
import cn.qinghe.mall.module.pay.framework.pay.core.client.*;
import cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay.AlipayPayClientConfig;
import org.springframework.boot.SpringApplication;import org.springframework.context.ConfigurableApplicationContext;import org.springframework.context.annotation.*;import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;import org.springframework.beans.factory.DisposableBean;
import java.util.concurrent.atomic.AtomicInteger;
/** DEC-076 test classpath only: real HTTP/auth/DB transactions, any vendor access fails immediately. */
public class CommercePayRegistrationLauncher {
 public static void main(String[] args){
  String expected="jdbc:mysql://127.0.0.1:3306/qinghe_pay_commerce_test?";
  if(!"commerce-registration-verification".equals(System.getenv("SPRING_PROFILES_ACTIVE"))||!"28087".equals(System.getenv("PAY_PORT"))||System.getenv("QINGHE_PAY_DB_URL")==null||!System.getenv("QINGHE_PAY_DB_URL").startsWith(expected))throw new IllegalStateException("Dedicated registration fixture only");
  ConfigurableApplicationContext c=SpringApplication.run(new Class<?>[]{PayServerApplication.class,RegistrationFixture.class},args);
  if(!(c.getBean(PayClientFactory.class) instanceof DenyVendorFactory)||!(c instanceof ServletWebServerApplicationContext)||((ServletWebServerApplicationContext)c).getWebServer().getPort()!=28087||!"127.0.0.1".equals(c.getEnvironment().getProperty("server.address"))||!c.getEnvironment().getProperty("spring.datasource.url","").startsWith(expected)){c.close();throw new IllegalStateException("Registration fixture runtime boundary mismatch");}
  System.out.println("REGISTRATION_ONLY_READY_NO_VENDOR_CLIENT");
 }
 @Configuration @Profile("commerce-registration-verification") public static class RegistrationFixture {
  @Bean @Primary public PayClientFactory registrationOnlyFactory(){return new DenyVendorFactory();}
 }
 static class DenyVendorFactory implements PayClientFactory,DisposableBean {
  final AtomicInteger attempts=new AtomicInteger();
  public PayClient<AlipayPayClientConfig> createAlipayQr(Long id,AlipayPayClientConfig config){attempts.incrementAndGet();throw new IllegalStateException("Registration verification forbids vendor access");}
  public void destroy(){System.out.println("REGISTRATION_ONLY_VENDOR_ATTEMPTS="+attempts.get());}
 }
}
