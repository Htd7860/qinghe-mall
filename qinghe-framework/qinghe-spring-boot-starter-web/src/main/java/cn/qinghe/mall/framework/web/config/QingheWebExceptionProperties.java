package cn.qinghe.mall.framework.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/** Business error codes whose warning logs may be suppressed; error responses are unchanged. */
@ConfigurationProperties(prefix = "qinghe.web.exception")
public class QingheWebExceptionProperties {

    private Set<Integer> ignoredBusinessErrorCodes = new HashSet<>();

    public Set<Integer> getIgnoredBusinessErrorCodes() {
        return ignoredBusinessErrorCodes;
    }

    public void setIgnoredBusinessErrorCodes(Set<Integer> ignoredBusinessErrorCodes) {
        this.ignoredBusinessErrorCodes = ignoredBusinessErrorCodes;
    }
}
