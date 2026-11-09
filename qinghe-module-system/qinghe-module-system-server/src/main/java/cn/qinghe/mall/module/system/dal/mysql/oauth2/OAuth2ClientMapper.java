package cn.qinghe.mall.module.system.dal.mysql.oauth2;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.module.system.dal.mysql.oauth2
 * @Project：qinghe-mall
 * @name：OAuth2ClientMapper
 * @Date：2026/10/24 12:45
 * @Filename：OAuth2ClientMapper
 */
@Mapper
public interface OAuth2ClientMapper extends BaseMapperX<OAuth2ClientDO> {

    default OAuth2ClientDO selectByClient(String clientId){
        if (clientId == null || !clientId.matches("[A-Za-z0-9:_-]{1,64}")) {
            throw new IllegalArgumentException("客户端标识格式不合法");
        }

        return selectOne(new LambdaQueryWrapper<OAuth2ClientDO>().eq(OAuth2ClientDO::getClientId,clientId));
    }
}
