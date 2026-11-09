package cn.qinghe.mall.module.pay;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
/** 独立支付Servlet服务；仅已登记的内部入口可调用。 */
@SpringBootApplication(exclude=UserDetailsServiceAutoConfiguration.class)
public class PayServerApplication {
    public static void main(String[] args){SpringApplication.run(PayServerApplication.class,args);}
}
