package cn.qinghe.mall.module.infra.service.file;

import cn.qinghe.mall.module.infra.dal.dataobject.file.FileConfigDO;
import cn.qinghe.mall.module.infra.dal.mysql.file.FileConfigMapper;
import cn.qinghe.mall.module.infra.framework.file.core.client.FileClient;
import cn.qinghe.mall.module.infra.framework.file.core.client.FileClientFactory;
import cn.qinghe.mall.module.infra.framework.file.core.client.local.LocalFileClientConfig;
import cn.qinghe.mall.module.infra.framework.file.core.enums.FileStorageEnum;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.Set;

import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception;
import static cn.qinghe.mall.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_NOT_EXISTS;

/**
 * 沿用 yudao 的“读配置 -> 创建/刷新工厂客户端”流程。
 * 当前直接读取配置，不引入异步刷新缓存及整套配置管理 CRUD。
 *
 * @author 芋道源码
 */
@Service
public class FileConfigServiceImpl implements FileConfigService {
    private final FileClientFactory fileClientFactory;
    private final FileConfigMapper fileConfigMapper;
    private final Validator validator;

    public FileConfigServiceImpl(FileClientFactory fileClientFactory,
                                 FileConfigMapper fileConfigMapper, Validator validator) {
        this.fileClientFactory = fileClientFactory;
        this.fileConfigMapper = fileConfigMapper;
        this.validator = validator;
    }

    @Override
    public FileClient getFileClient(Long id) {
        return loadClient(id == null ? null : fileConfigMapper.selectById(id));
    }

    @Override
    public FileClient getMasterFileClient() {
        return loadClient(fileConfigMapper.selectByMaster());
    }

    private FileClient loadClient(FileConfigDO row) {
        if (row == null) {
            throw exception(FILE_CONFIG_NOT_EXISTS);
        }
        if (!Integer.valueOf(FileStorageEnum.LOCAL.getStorage()).equals(row.getStorage())
                || row.getConfig() == null) {
            throw new IllegalStateException("文件存储配置不受支持");
        }
        Set<ConstraintViolation<LocalFileClientConfig>> violations = validator.validate(row.getConfig());
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        // 与工厂缓存的初始化/刷新一起串行，避免同一编号首次获取时重复创建。
        synchronized (fileClientFactory) {
            fileClientFactory.createOrUpdateFileClient(row.getId(), row.getStorage(), row.getConfig());
            return fileClientFactory.getFileClient(row.getId());
        }
    }
}
