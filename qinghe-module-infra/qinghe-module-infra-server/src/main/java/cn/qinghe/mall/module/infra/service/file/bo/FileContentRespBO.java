package cn.qinghe.mall.module.infra.service.file.bo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件内容与可信数据库元数据，不在日志中输出二进制。 */
@Getter
@AllArgsConstructor
public class FileContentRespBO {
    private final String name;
    private final String type;
    private final byte[] content;
}
