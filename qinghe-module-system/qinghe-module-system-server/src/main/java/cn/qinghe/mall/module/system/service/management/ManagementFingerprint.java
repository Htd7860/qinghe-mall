package cn.qinghe.mall.module.system.service.management;
import cn.qinghe.mall.framework.common.util.json.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/** 仅对服务端按固定顺序构造的配置快照求摘要；不是密码算法或认证凭据。 */
public final class ManagementFingerprint {
    private ManagementFingerprint() { }
    public static String sha256(Object canonical) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(canonical).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte value:bytes) { hex.append(String.format("%02x",value & 255)); }
            return hex.toString();
        } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("运行环境缺少SHA-256"); }
    }
}
