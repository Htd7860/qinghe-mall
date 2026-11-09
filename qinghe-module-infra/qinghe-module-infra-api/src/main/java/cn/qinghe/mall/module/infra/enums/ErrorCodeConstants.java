package cn.qinghe.mall.module.infra.enums;

import cn.qinghe.mall.framework.common.exception.ErrorCode;

/**
 * 文件错误码，取自 yudao-cloud infra 模块，保留原编号与语义。
 *
 * @author 芋道源码
 */
public interface ErrorCodeConstants {

    ErrorCode FILE_PATH_EXISTS = new ErrorCode(1_001_003_000, "文件路径已存在");
    ErrorCode FILE_NOT_EXISTS = new ErrorCode(1_001_003_001, "文件不存在");
    ErrorCode FILE_IS_EMPTY = new ErrorCode(1_001_003_002, "文件为空");
    ErrorCode FILE_PATH_INVALID = new ErrorCode(1_001_003_003, "文件路径不正确");
    ErrorCode FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_001_003_004, "不支持的文件类型");
    // 本项目上传资源保护边界；其余文件错误码沿用 yudao。
    ErrorCode FILE_SIZE_EXCEEDED = new ErrorCode(1_001_003_005, "文件大小超过限制");

    ErrorCode FILE_CONFIG_NOT_EXISTS = new ErrorCode(1_001_006_000, "文件配置不存在");
    ErrorCode FILE_CONFIG_DELETE_FAIL_MASTER = new ErrorCode(1_001_006_001,
            "该文件配置不允许删除，原因：它是主配置，删除会导致无法上传文件");
}
