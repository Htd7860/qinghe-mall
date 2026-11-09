package cn.qinghe.mall.module.member.service.auth;

import cn.qinghe.mall.module.system.api.oauth2.dto.MemberTokenIssueRespDTO;

/** 原会员手机号密码登录职责；仅供已按可信Host解析店铺的后端调用。
 * 无公开HTTP，调用者须先做来源/账号组合保护，不得把浏览器自报tenantId当授权。
 * @author 芋道源码
 */
public interface MemberAuthService {
    MemberTokenIssueRespDTO login(Long tenantId,String mobile,String password);
}
