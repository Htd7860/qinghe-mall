package cn.qinghe.mall.module.infra.service.file;

import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.service.SecurityFrameworkService;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindReqDTO;
import cn.qinghe.mall.module.infra.api.file.dto.FileBusinessBindRespDTO;
import cn.qinghe.mall.module.infra.dal.dataobject.file.FileDO;
import cn.qinghe.mall.module.infra.dal.mysql.file.FileMapper;
import cn.qinghe.mall.module.infra.dal.mysql.file.FileReferenceMapper;
import cn.qinghe.mall.module.infra.enums.FilePurposeEnum;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 文件真实归属检查和引用保护，是本项目多租户图片关联增量。 */
@Service
public class FileBusinessService {
    private final FileMapper files;
    private final FileReferenceMapper references;
    private final SecurityFrameworkService security;

    public FileBusinessService(FileMapper files, FileReferenceMapper references, SecurityFrameworkService security) {
        this.files = files; this.references = references; this.security = security;
    }

    @Transactional(rollbackFor = Exception.class, timeout = 10)
    public FileBusinessBindRespDTO bind(FileBusinessBindReqDTO body) {
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (actor == null || !UserTypeEnum.ADMIN.getValue().equals(actor.getUserType())
                || actor.getTenantId() == null || actor.getTenantId() <= 0 || TenantContextHolder.isIgnore()
                || !Objects.equals(actor.getTenantId(), TenantContextHolder.getTenantId())) {
            throw new AccessDeniedException("图片关联身份不匹配");
        }
        String permission;
        switch (body.getResourceType()) {
            case "CATEGORY": permission = "product:category:"; break;
            case "BRAND": permission = "product:brand:"; break;
            case "SPU": permission = "product:spu:"; break;
            default: throw exception0(400, "图片关联用途不正确");
        }
        if (!security.hasAnyPermissions(permission + "create", permission + "update")) {
            throw new AccessDeniedException("没有图片关联权限");
        }
        FileDO file = files.selectByUrlForUpdate(body.getUrl());
        if (file == null || !Objects.equals(file.getUrl(), body.getUrl())
                || !FilePurposeEnum.PRODUCT_IMAGE.getValue().equals(file.getPurpose())) {
            throw exception0(404, "商品图片不存在或不属于当前租户");
        }
        boolean bound = references.countResource(actor.getTenantId(), file.getId(),
                body.getResourceType(), body.getResourceId()) > 0;
        if (!bound && (!Objects.equals(file.getOwnerUserId(), actor.getId())
                || !Objects.equals(file.getOwnerUserType(), actor.getUserType()))) {
            throw new AccessDeniedException("只能新增关联本人上传的商品图片");
        }
        references.bind(actor.getTenantId(), file.getId(), body.getResourceType(), body.getResourceId(), actor.getId());
        if (references.countResource(actor.getTenantId(), file.getId(), body.getResourceType(), body.getResourceId()) != 1) {
            throw new IllegalStateException("文件引用登记结果异常");
        }
        FileBusinessBindRespDTO result = new FileBusinessBindRespDTO();
        result.setId(file.getId()); result.setUrl(file.getUrl());
        return result;
    }
}
