package cn.qinghe.mall.module.member.service.auth;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 择取yudao MemberUserServiceImpl.isPasswordMatch，保留BCrypt编码内置盐语义。
 * @author 芋道源码
 */
@Service
public class MemberPasswordService {
    private final PasswordEncoder encoder;
    private final String dummy;
    public MemberPasswordService(PasswordEncoder encoder) {
        this.encoder=encoder;this.dummy=encoder.encode(UUID.randomUUID().toString());
    }
    public boolean isPasswordMatch(String raw,String encoded) {
        if (!validInput(raw)) { return false; }
        requireEncoding(encoded);
        return encoder.matches(raw,encoded);
    }
    /** 新设置密码的规则独立于历史密码匹配，不裁剪、不改变大小写。 */
    public String encodeNewPassword(String raw) {
        if (!validInput(raw) || raw.codePointCount(0,raw.length())<8
                || raw.codePoints().anyMatch(Character::isISOControl)) {
            throw exception0(400,"密码至少8个字符、最多72个UTF-8字节，且不能含控制字符");
        }
        String encoded=encoder.encode(raw);
        requireEncoding(encoded);
        return encoded;
    }
    private void requireEncoding(String encoded) {
        if (encoded==null || !encoded.matches("^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$")) {
            throw exception0(503,"会员密码配置异常");
        }
        int cost=Integer.parseInt(encoded.substring(4,6));
        // 本项目资源边界，避免异常数据库成本参数触发无界密码计算。
        if (cost<4 || cost>16) { throw exception0(503,"会员密码成本配置异常"); }
    }
    public boolean validInput(String raw) {
        return raw!=null && !raw.isEmpty() && raw.length()<=72 && StandardCharsets.UTF_8.newEncoder().canEncode(raw)
                && raw.getBytes(StandardCharsets.UTF_8).length<=72;
    }
    public void matchUnknown(String raw) { if (validInput(raw)) { encoder.matches(raw,dummy); } }
}
