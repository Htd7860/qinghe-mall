package cn.qinghe.mall.module.system.service.tenant;
import java.net.IDN;
import java.util.Locale;
/** 仅规范化HTTP Host，绝不做DNS/URL访问或读取客户端转发头。 */
public final class StorefrontHost {
    private StorefrontHost() { }
    public static String normalize(String value) {
        if (value==null || value.isEmpty() || value.length()>300 || value.chars().anyMatch(ch -> Character.isWhitespace(ch)
                || Character.isISOControl(ch) || "/\\@?#*,%[]".indexOf(ch)>=0)) { throw new IllegalArgumentException("入口Host格式不正确"); }
        String host=value;Integer port=null;int colon=host.indexOf(':');
        if (colon>=0) {
            if (colon!=host.lastIndexOf(':')) { throw new IllegalArgumentException("入口Host格式不正确"); }
            String number=host.substring(colon+1);host=host.substring(0,colon);
            if (!number.matches("[0-9]{1,5}")) { throw new IllegalArgumentException("入口端口不正确"); }
            port=Integer.valueOf(number);if (port<1 || port>65535) { throw new IllegalArgumentException("入口端口不正确"); }
        }
        if (host.endsWith(".")) { host=host.substring(0,host.length()-1); }
        host=IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (host.isEmpty() || host.length()>253 || host.endsWith(".") || !host.matches("[a-z0-9.-]+")) { throw new IllegalArgumentException("入口域名不正确"); }
        if (port!=null && !loopbackName(host)) {
            if (port!=443) { throw new IllegalArgumentException("域名入口不接受自定义端口"); }
            port=null;
        }
        return port==null?host:host+":"+port;
    }
    public static boolean isLocal(String canonical) {
        int colon=canonical.indexOf(':');return colon>0 && loopbackName(canonical.substring(0,colon));
    }
    private static boolean loopbackName(String host) { return "localhost".equals(host) || "127.0.0.1".equals(host); }
}
