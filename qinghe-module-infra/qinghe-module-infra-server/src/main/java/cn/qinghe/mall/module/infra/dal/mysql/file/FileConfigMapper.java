package cn.qinghe.mall.module.infra.dal.mysql.file;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.module.infra.dal.dataobject.file.FileConfigDO;
import org.apache.ibatis.annotations.Mapper;

/** 取自 yudao FileConfigMapper；当前先接入文件服务所需的配置读取。 */
@Mapper
public interface FileConfigMapper extends BaseMapperX<FileConfigDO> {
    default FileConfigDO selectByMaster() {
        return selectOne(new LambdaQueryWrapperX<FileConfigDO>().eq(FileConfigDO::getMaster, true));
    }
}
