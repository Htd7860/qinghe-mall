package cn.qinghe.mall.module.member.controller.app.auth.vo;

import lombok.Data;
import java.util.Collections;
import java.util.List;

/** 当前消费者身份，复用三端Identity形状；不包含手机号、Token、密码或后台角色资源。 */
@Data
public class AppAuthSessionRespVO {
    private String realm="member";
    private Long userId;
    private String name;
    private String tenantId;
    private String tenantCode;
    private String role="会员";
    private List<String> permissions=Collections.emptyList();
    private Long expiresAt;
}
