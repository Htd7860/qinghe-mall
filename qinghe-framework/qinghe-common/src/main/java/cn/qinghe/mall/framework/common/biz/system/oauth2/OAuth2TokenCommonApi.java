package cn.qinghe.mall.framework.common.biz.system.oauth2;

import cn.qinghe.mall.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.qinghe.mall.framework.common.pojo.CommonResult;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.common.biz.system.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2TokenCommonApi
 * @Date：2026/10/22 20:58
 * @Filename：OAuth2TokenCommonApi
 */
public interface OAuth2TokenCommonApi {
    CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessToken(
            String accessToken);

}
