package cn.qinghe.mall.module.infra.service.file;

import cn.qinghe.mall.framework.common.pojo.PageResult;
import cn.qinghe.mall.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.qinghe.mall.module.infra.dal.dataobject.file.FileDO;
import cn.qinghe.mall.module.infra.enums.FilePurposeEnum;
import cn.qinghe.mall.module.infra.service.file.bo.FileContentRespBO;

/** 复用 yudao 文件服务职责；租户、上传者及可公开用途由可信后端约束。 */
public interface FileService {
    PageResult<FileDO> getFilePage(FilePageReqVO reqVO);
    FileDO createFile(byte[] content, String name, FilePurposeEnum purpose);
    FileDO getFile(Long id);
    void deleteFile(Long id) throws Exception;
    FileContentRespBO getFileContent(Long id) throws Exception;
    FileContentRespBO getPublicFileContent(Long configId, String path) throws Exception;
}
