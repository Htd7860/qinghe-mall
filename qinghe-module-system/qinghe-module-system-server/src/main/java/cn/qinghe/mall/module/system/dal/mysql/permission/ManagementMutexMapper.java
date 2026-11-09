package cn.qinghe.mall.module.system.dal.mysql.permission;

import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils;
import cn.qinghe.mall.module.system.dal.dataobject.permission.ManagementMutexDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ManagementMutexMapper extends BaseMapperX<ManagementMutexDO> {
    default ManagementMutexDO lockManagement() {
        MybatisUtils.requireWriteTransaction();
        return selectOne(new LambdaQueryWrapper<ManagementMutexDO>()
                .eq(ManagementMutexDO::getId, 1L).last("FOR UPDATE"));
    }
}
