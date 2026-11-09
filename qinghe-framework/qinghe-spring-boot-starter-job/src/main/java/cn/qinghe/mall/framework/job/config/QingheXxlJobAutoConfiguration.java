package cn.qinghe.mall.framework.job.config;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import java.net.URI;
import java.util.Arrays;

/** 直接择取原执行器Bean初始化；不额外开启Spring定时扫描，默认无后台任务运行。
 * @author 芋道源码
 */
@AutoConfiguration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@ConditionalOnProperty(prefix="xxl.job",name="enabled",havingValue="true",matchIfMissing=false)
@EnableConfigurationProperties(XxlJobProperties.class)
public class QingheXxlJobAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(XxlJobExecutor.class)
    public XxlJobExecutor xxlJobExecutor(XxlJobProperties properties) {
        for(String address:properties.getAdmin().getAddresses().split(",",-1)) {
            URI uri=URI.create(address);
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && Arrays.asList("localhost","127.0.0.1","::1","[::1]").contains(uri.getHost())))
                throw new IllegalArgumentException("XXL调度器须为HTTPS或本机调试地址");
        }
        XxlJobProperties.ExecutorProperties config=properties.getExecutor();
        XxlJobExecutor executor=new XxlJobSpringExecutor();
        executor.setIp(config.getIp());executor.setPort(config.getPort());executor.setAppname(config.getAppName());
        executor.setLogPath(config.getLogPath());executor.setLogRetentionDays(config.getLogRetentionDays());
        executor.setAdminAddresses(properties.getAdmin().getAddresses());executor.setAccessToken(properties.getAccessToken());
        return executor;
    }
}
