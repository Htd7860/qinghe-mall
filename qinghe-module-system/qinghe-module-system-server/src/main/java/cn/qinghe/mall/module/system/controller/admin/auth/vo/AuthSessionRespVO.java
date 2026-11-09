package cn.qinghe.mall.module.system.controller.admin.auth.vo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/** 当前Bearer身份投影，沿用前端Identity字段；不是服务端HttpSession。 */
@Data
public class AuthSessionRespVO {

    private String realm;
    private Long userId;
    private String name;
    /** 前端契约使用字符串，避免租户Long编号经JavaScript丢失精度。 */
    private String tenantId;
    /** 商家为真实稳定编码；平台为空字符串。 */
    private String tenantCode;
    /** 当前有效角色名称拼接，仅用于展示；没有启用角色时为空。 */
    private String role = "";
    /** 当前有效角色资源与套餐范围内的精确权限标识。 */
    private List<String> permissions = Collections.emptyList();
    /** 当前accessToken到期时间，epoch毫秒，不延长会话。 */
    private Long expiresAt;
}
