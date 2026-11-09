package cn.qinghe.mall.module.system.api.oauth2;

import cn.qinghe.mall.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.system.service.oauth2.OAuth2TokenService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 本进程的Token校验适配，不暴露公共HTTP凭据校验接口。 */
@Service
public class OAuth2TokenCommonApiImpl implements OAuth2TokenCommonApi {

    private final OAuth2TokenService tokenService;

    public OAuth2TokenCommonApiImpl(OAuth2TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessToken(
            String accessToken) {
        OAuth2AccessTokenCheckRespDTO result = Objects.requireNonNull(
                tokenService.checkAccessToken(accessToken),
                "令牌校验结果不能为空");
        return CommonResult.success(result);
    }
}
