package cn.qinghe.mall.module.system.dal.mysql.oauth2;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.mysql.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2AccessTokenMapper
 * @Date：2026/10/23 20:29
 * @Filename：OAuth2AccessTokenMapper
 */
@Mapper
public interface OAuth2AccessTokenMapper extends BaseMapperX<OAuth2AccessTokenDO> {

    default OAuth2AccessTokenDO selectByAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken) || accessToken.length() > 128
                || accessToken.chars().anyMatch(ch -> Character.isWhitespace(ch)
                || Character.isSpaceChar(ch)
                || Character.isISOControl(ch)
                || ch == ',')) {
            throw new IllegalArgumentException("访问令牌格式不合法");
        }

        return TenantUtils.executeIgnore(() ->
                selectOne(new LambdaQueryWrapper<OAuth2AccessTokenDO>()
                        .eq(OAuth2AccessTokenDO::getAccessToken, accessToken)));
    }
    /**
     * 在可信租户内锁定同一刷新凭据关联的访问记录。
     * 调用方必须处于可写事务，并且已先锁住父刷新记录。
     */
    default List<OAuth2AccessTokenDO> selectListByRefreshTokenForUpdate(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || refreshToken.length() > 128
                || refreshToken.chars().anyMatch(ch -> Character.isWhitespace(ch)
                || Character.isSpaceChar(ch) || Character.isISOControl(ch) || ch == ',')) {
            throw new IllegalArgumentException("刷新令牌格式不合法");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("关联令牌查询需要可信租户隔离");
        }
        return selectList(new LambdaQueryWrapper<OAuth2AccessTokenDO>()
                .eq(OAuth2AccessTokenDO::getRefreshToken,refreshToken)
                .eq(OAuth2AccessTokenDO::getTenantId,tenantId)
                .orderByAsc(OAuth2AccessTokenDO::getId).last("FOR UPDATE"));
    }
}
