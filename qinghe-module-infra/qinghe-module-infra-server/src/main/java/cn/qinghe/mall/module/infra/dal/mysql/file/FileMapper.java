package cn.qinghe.mall.module.infra.dal.mysql.file;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.mapper.BaseMapperX;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.qinghe.mall.module.infra.dal.dataobject.file.FileDO;
import org.apache.ibatis.annotations.Mapper;

/** 文件分页沿用 yudao，普通读取显式限制当前租户与上传者。 */
@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {
    default FileDO selectByUrlForUpdate(String url) {
        cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils.requireWriteTransaction();
        return selectOne(new LambdaQueryWrapperX<FileDO>().eq(FileDO::getTenantId, requiredTenantId())
                .eq(FileDO::getUrl, url).last("FOR UPDATE"));
    }

    default FileDO selectByIdForUpdate(Long id) {
        cn.qinghe.mall.framework.mybatis.core.util.MybatisUtils.requireWriteTransaction();
        return selectOne(new LambdaQueryWrapperX<FileDO>().eq(FileDO::getTenantId, requiredTenantId())
                .eq(FileDO::getId, id).last("FOR UPDATE"));
    }

    default PageResult<FileDO> selectPage(FilePageReqVO reqVO, Long userId, Integer userType) {
        Long tenantId = requiredTenantId();
        return selectPage(reqVO, new LambdaQueryWrapperX<FileDO>()
                .likeIfPresent(FileDO::getPath, reqVO.getPath())
                .likeIfPresent(FileDO::getType, reqVO.getType())
                .betweenIfPresent(FileDO::getCreateTime, reqVO.getCreateTime())
                .eq(FileDO::getTenantId, tenantId)
                .eq(FileDO::getOwnerUserId, userId)
                .eq(FileDO::getOwnerUserType, userType)
                .orderByDesc(FileDO::getId));
    }

    default FileDO selectByIdAndTenant(Long id) {
        return selectOne(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getId, id).eq(FileDO::getTenantId, requiredTenantId()));
    }

    default Long requiredTenantId() {
        if (TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("普通文件访问不能忽略租户");
        }
        return TenantContextHolder.getRequiredTenantId();
    }
}
