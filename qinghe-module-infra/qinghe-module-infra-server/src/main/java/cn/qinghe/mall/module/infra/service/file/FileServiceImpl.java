package cn.qinghe.mall.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.qinghe.mall.framework.common.enums.UserTypeEnum;
import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.framework.tenant.core.util.TenantUtils;
import cn.qinghe.mall.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.qinghe.mall.module.infra.dal.dataobject.file.FileDO;
import cn.qinghe.mall.module.infra.dal.mysql.file.FileMapper;
import cn.qinghe.mall.module.infra.dal.mysql.file.FileReferenceMapper;
import cn.qinghe.mall.module.infra.enums.FilePurposeEnum;
import cn.qinghe.mall.module.infra.framework.file.config.FileUploadProperties;
import cn.qinghe.mall.module.infra.framework.file.core.client.FileClient;
import cn.qinghe.mall.module.infra.framework.file.core.utils.FilePathUtils;
import cn.qinghe.mall.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.qinghe.mall.module.infra.service.file.bo.FileContentRespBO;
import lombok.SneakyThrows;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static cn.hutool.core.date.DatePattern.PURE_DATE_PATTERN;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
import static cn.qinghe.mall.module.infra.enums.ErrorCodeConstants.*;

/**
 * 取自 yudao FileServiceImpl 的检查、生成路径、上传、落库和删除流程。
 * 项目差异：可信租户/上传者、用途决定公开性、10 MiB 图片策略、随机路径防覆盖。
 * 不提供可任意注册路径的前端直传入口；文件与数据库不是同一原子事务。
 *
 * @author 芋道源码
 */
@Service
public class FileServiceImpl implements FileService {
    private static final Set<String> ALLOWED_FILE_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp")));

    private final FileConfigService fileConfigService;
    private final FileMapper fileMapper;
    private final FileUploadProperties properties;
    private final FileReferenceMapper references;

    public FileServiceImpl(FileConfigService fileConfigService, FileMapper fileMapper,
                           FileUploadProperties properties, FileReferenceMapper references) {
        this.fileConfigService = fileConfigService;
        this.fileMapper = fileMapper;
        this.properties = properties;
        this.references = references;
    }

    @Override
    public PageResult<FileDO> getFilePage(FilePageReqVO reqVO) {
        LoginUser actor = requireActor();
        return fileMapper.selectPage(reqVO, actor.getId(), actor.getUserType());
    }

    @Override
    @SneakyThrows
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileDO createFile(byte[] content, String name, FilePurposeEnum purpose) {
        LoginUser actor = requireActor();
        requirePurpose(actor, purpose);
        name = FilePathUtils.validateFileName(name);
        if (name != null && (name.length() > 255 || name.chars().anyMatch(Character::isISOControl))) {
            throw exception(FILE_PATH_INVALID);
        }
        if (content == null || content.length == 0) {
            throw exception(FILE_IS_EMPTY);
        }
        if (content.length > properties.getMaxBytes()) {
            throw exception(FILE_SIZE_EXCEEDED);
        }

        // 沿用原版真实内容识别；不接受客户端提交的 MIME 类型作为判断依据。
        String type = FileTypeUtils.getMineType(content);
        String nameType = StrUtil.isNotEmpty(FileUtil.extName(name)) ? FileTypeUtils.getMineType(name) : null;
        if (!ALLOWED_FILE_TYPES.contains(type) || nameType != null && !type.equals(nameType)) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
        String extension = FileTypeUtils.getExtension(type);
        if (StrUtil.isEmpty(extension)) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
        if (StrUtil.isEmpty(name)) {
            name = IdUtil.fastSimpleUUID() + extension;
        } else if (StrUtil.isEmpty(FileUtil.extName(name))) {
            name += extension;
        }
        if (name.length() > 255) {
            throw exception(FILE_PATH_INVALID);
        }

        String path = generateUploadPath(actor.getTenantId(), purpose, extension);
        FileClient client = fileConfigService.getMasterFileClient();
        String url = client.upload(content, path, type);

        LocalDateTime now = LocalDateTime.now();
        FileDO file = new FileDO();
        file.setTenantId(actor.getTenantId());
        file.setOwnerUserId(actor.getId());
        file.setOwnerUserType(actor.getUserType());
        file.setPurpose(purpose.getValue());
        file.setConfigId(client.getId());
        file.setName(name);
        file.setPath(path);
        file.setUrl(url);
        file.setType(type);
        file.setSize((long) content.length);
        file.setCreator(actor.getId().toString());
        file.setUpdater(actor.getId().toString());
        file.setCreateTime(now);
        file.setUpdateTime(now);
        file.setDeleted(false);
        // 独立于调用者事务，避免上传成功记录因外层业务回滚消失；不宣称文件与DB原子。
        // 数据库写入失败/结果未知时保留随机路径的文件，不能盲删可能已提交的文件。
        if (fileMapper.insert(file) != 1 || file.getId() == null) {
            throw new IllegalStateException("文件记录保存结果异常");
        }
        return file;
    }

    String generateUploadPath(Long tenantId, FilePurposeEnum purpose, String extension) {
        String date = LocalDateTimeUtil.format(LocalDateTimeUtil.now(), PURE_DATE_PATTERN);
        String path = "tenant/" + tenantId + "/" + purpose.getValue() + "/" + date
                + "/" + IdUtil.fastSimpleUUID() + extension;
        FilePathUtils.validatePath(path);
        return path;
    }

    @Override
    public FileDO getFile(Long id) {
        LoginUser actor = requireActor();
        FileDO file = fileMapper.selectByIdAndTenant(id);
        if (file == null || !Objects.equals(file.getOwnerUserId(), actor.getId())
                || !Objects.equals(file.getOwnerUserType(), actor.getUserType())) {
            throw exception0(404, FILE_NOT_EXISTS.getMsg());
        }
        return file;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class, timeout = 10)
    public void deleteFile(Long id) throws Exception {
        LoginUser actor = requireActor();
        FileDO file = fileMapper.selectByIdForUpdate(id);
        if (file == null || !Objects.equals(file.getOwnerUserId(), actor.getId())
                || !Objects.equals(file.getOwnerUserType(), actor.getUserType())) {
            throw exception0(404, FILE_NOT_EXISTS.getMsg());
        }
        if (references.countFile(actor.getTenantId(), id) > 0) {
            throw exception0(409, "图片已关联业务资料，不能直接删除");
        }
        FilePathUtils.validatePath(file.getPath());
        // 顺序沿用原版：删除存储对象，再逻辑删除记录；失败可按同一id重试。
        fileConfigService.getFileClient(file.getConfigId()).delete(file.getPath());
        if (fileMapper.deleteById(id) != 1) {
            throw new IllegalStateException("文件记录删除结果异常");
        }
    }

    @Override
    public FileContentRespBO getFileContent(Long id) throws Exception {
        return readContent(getFile(id));
    }

    @Override
    public FileContentRespBO getPublicFileContent(Long configId, String path) throws Exception {
        FilePathUtils.validatePath(path);
        // 唯一的跨租户公开读取：必须先匹配真实元数据及允许公开的用途。
        // 绝不按外部路径直接调用 client.getContent；私有凭证和已删文件不会命中。
        FileDO file = TenantUtils.executeIgnore(() -> fileMapper.selectOne(new LambdaQueryWrapperX<FileDO>()
                .eq(FileDO::getConfigId, configId).eq(FileDO::getPath, path)
                .in(FileDO::getPurpose, FilePurposeEnum.PRODUCT_IMAGE.getValue(), FilePurposeEnum.STORE_LOGO.getValue())));
        if (file == null) {
            throw exception0(404, FILE_NOT_EXISTS.getMsg());
        }
        return readContent(file);
    }

    private FileContentRespBO readContent(FileDO file) throws Exception {
        FilePathUtils.validatePath(file.getPath());
        byte[] content = fileConfigService.getFileClient(file.getConfigId()).getContent(file.getPath());
        if (content == null) {
            throw exception0(404, FILE_NOT_EXISTS.getMsg());
        }
        if (content.length != file.getSize()) {
            throw new IllegalStateException("存储文件与记录大小不一致");
        }
        return new FileContentRespBO(file.getName(), file.getType(), content);
    }

    private LoginUser requireActor() {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw exception0(401, "请先登录");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId < 0 || TenantContextHolder.isIgnore() || !Objects.equals(tenantId, user.getTenantId())
                || (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                && !UserTypeEnum.MEMBER.getValue().equals(user.getUserType()))) {
            throw new AccessDeniedException("文件访问身份与租户不匹配");
        }
        return user;
    }

    private void requirePurpose(LoginUser user, FilePurposeEnum purpose) {
        if (purpose == null) {
            throw exception0(400, "请选择文件用途");
        }
        if (purpose.isPublicRead() && !UserTypeEnum.ADMIN.getValue().equals(user.getUserType())) {
            throw new AccessDeniedException("无权上传公开经营图片");
        }
        if (user.getTenantId() == 0 && purpose != FilePurposeEnum.STORE_LOGO) {
            throw new AccessDeniedException("文件用途与平台身份不匹配");
        }
    }
}
