package cn.qinghe.mall.module.infra.service.file;

import cn.qinghe.mall.module.infra.framework.file.core.client.FileClient;

/** yudao 文件配置服务中当前需要的两个客户端获取入口。 */
public interface FileConfigService {
    FileClient getFileClient(Long id);
    FileClient getMasterFileClient();
}
