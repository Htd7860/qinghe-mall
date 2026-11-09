package cn.qinghe.mall.module.system.service.oauth2;

import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2ClientDO;

import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.service.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2ClientService
 * @Date：2026/10/24 13:12
 * @Filename：OAuth2ClientService
 */
public interface OAuth2ClientService {
    OAuth2ClientDO validateClient(String clientId, Integer userType, Long tenantId, List<String> scopes);
}
