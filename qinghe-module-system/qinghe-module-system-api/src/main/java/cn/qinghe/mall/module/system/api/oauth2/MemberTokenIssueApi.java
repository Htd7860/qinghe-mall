package cn.qinghe.mall.module.system.api.oauth2;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueReqDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueRespDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenRefreshReqDTO;
import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenLogoutReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 择取原OAuth2TokenCommonApi/OAuth2TokenApiImpl.createAccessToken的服务间签发职责；固定MEMBER/消费者入口。
 * 签发须先验证会员密码，刷新/退出须持有对应凭据；不可把浏览器自报用户编号作为认证结果。
 * @author 芋道源码
 */
@FeignClient(name="qinghe-system-member-token",contextId="qingheMemberTokenIssuer",
        url="${qinghe.member-token.system-base-url:http://127.0.0.1:8080}",
        configuration=MemberTokenFeignConfiguration.class)
public interface MemberTokenIssueApi {
    String PATH="/internal-api/system/member-token/issue";
    String REFRESH_PATH="/internal-api/system/member-token/refresh";
    String LOGOUT_PATH="/internal-api/system/member-token/logout";
    @PostMapping(PATH)
    CommonResult<MemberTokenIssueRespDTO> issue(@RequestHeader("Authorization") String serviceAuthorization,
            @RequestBody MemberTokenIssueReqDTO body);
    @PostMapping(REFRESH_PATH)
    CommonResult<MemberTokenIssueRespDTO> refresh(@RequestHeader("Authorization") String serviceAuthorization,
            @RequestBody MemberTokenRefreshReqDTO body);
    @PostMapping(LOGOUT_PATH)
    CommonResult<Boolean> logout(@RequestHeader("Authorization") String serviceAuthorization,
            @RequestBody MemberTokenLogoutReqDTO body);
}
