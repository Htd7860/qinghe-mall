package cn.qinghe.mall.framework.common.util.trace;
import org.slf4j.MDC;
import java.util.UUID;

/** 日志相关编号，不是身份凭据、签名或完整分布式Span。 */
public final class TraceUtils {
    public static final String HEADER="trace-id";
    public static final String KEY="traceId";
    public static final String AUTHENTICATED_TENANT_ATTRIBUTE="qinghe.log.authenticatedTenantId";
    public static final String AUTHENTICATED_ACTOR_ATTRIBUTE="qinghe.log.authenticatedActorId";
    private TraceUtils() {}
    public static String create(){return UUID.randomUUID().toString();}
    public static boolean valid(String value){return value!=null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");}
    public static String current(){String value=MDC.get(KEY);return valid(value)?value:null;}
}
