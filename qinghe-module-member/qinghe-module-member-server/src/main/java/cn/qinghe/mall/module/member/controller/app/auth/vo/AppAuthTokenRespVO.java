package cn.qinghe.mall.module.member.controller.app.auth.vo;

import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueRespDTO;
import lombok.Data;
import lombok.ToString;
import java.time.ZoneId;

/** 原登录令牌结果的浏览器投影；与当前后台契约一致使用epoch毫秒，不带身份资料。
 * @author 芋道源码
 */
@Data
@ToString(onlyExplicitlyIncluded=true)
public class AppAuthTokenRespVO {
    private String accessToken;
    private String refreshToken;
    private Long expiresAt;
    private Long refreshExpiresAt;

    public static AppAuthTokenRespVO from(MemberTokenIssueRespDTO pair) {
        // 沿用当前内部LocalDateTime协议；system/member/gateway部署必须使用一致JVM时区。
        ZoneId zone=ZoneId.systemDefault();
        AppAuthTokenRespVO result=new AppAuthTokenRespVO();
        result.setAccessToken(pair.getAccessToken());result.setRefreshToken(pair.getRefreshToken());
        result.setExpiresAt(pair.getExpiresTime().atZone(zone).toInstant().toEpochMilli());
        result.setRefreshExpiresAt(pair.getRefreshExpiresTime().atZone(zone).toInstant().toEpochMilli());
        return result;
    }
}
