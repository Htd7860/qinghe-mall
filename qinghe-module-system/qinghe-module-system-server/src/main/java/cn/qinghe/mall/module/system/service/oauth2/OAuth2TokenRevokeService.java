package cn.qinghe.mall.module.system.service.oauth2;

/** 内部会话撤销；浏览器退出的受控HTTP入口尚待接入，沿用主动Bearer方案。 */
public interface OAuth2TokenRevokeService {

    /** 撤销当前访问凭据关联的会话；clientId由服务端入口确定，重复调用幂等。 */
    void revokeAccessToken(String accessToken, String clientId);
}
