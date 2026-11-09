package cn.qinghe.mall.module.system.dal.dataobject.oauth2;

import cn.qinghe.mall.framework.mybatis.core.dataobject.BaseDo;
import cn.qinghe.mall.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.dataobject.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2ClientDO
 * @Date：2026/10/24 11:31
 * @Filename：OAuth2ClientDO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
@TableName(value = "system_oauth2_client", autoResultMap = true)
@TenantIgnore(enable = "true")
public class OAuth2ClientDO extends BaseDo {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端标识，与 Token 中的 clientId 对应。 */
    private String clientId;

    /** 应用名称。 */
    private String name;

    /** 0：开启；1：关闭。 */
    private Integer status;

    /** 所属入口：platform、merchant、consumer。 */
    private String realm;

    /** 访问令牌有效期，单位：秒。 */
    private Integer accessTokenValiditySeconds;

    /** 刷新令牌有效期，单位：秒。 */
    private Integer refreshTokenValiditySeconds;

    /** 该客户端允许的授权范围。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scopes;
}
