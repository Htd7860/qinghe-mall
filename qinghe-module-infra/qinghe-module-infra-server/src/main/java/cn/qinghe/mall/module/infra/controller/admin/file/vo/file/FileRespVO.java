package cn.qinghe.mall.module.infra.controller.admin.file.vo.file;

import cn.qinghe.mall.module.infra.dal.dataobject.file.FileDO;
import cn.qinghe.mall.module.infra.enums.FilePurposeEnum;
import lombok.Data;
import java.time.LocalDateTime;

/** yudao文件响应投影；不对外返回存储配置或绝对磁盘路径。 */
@Data
public class FileRespVO {
    private Long id;
    private String name;
    private String type;
    private Long size;
    private String purpose;
    private String url;
    private String contentPath;
    private LocalDateTime createTime;

    public static FileRespVO from(FileDO file, String apiPrefix) {
        FileRespVO result = new FileRespVO();
        result.setId(file.getId());
        result.setName(file.getName());
        result.setType(file.getType());
        result.setSize(file.getSize());
        result.setPurpose(file.getPurpose());
        result.setCreateTime(file.getCreateTime());
        result.setContentPath(apiPrefix + "/infra/file/content?id=" + file.getId());
        // 私有内容只能通过contentPath携带Bearer读取，不返回可匿名使用的URL。
        result.setUrl(FilePurposeEnum.fromValue(file.getPurpose()).isPublicRead() ? file.getUrl() : null);
        return result;
    }
}
