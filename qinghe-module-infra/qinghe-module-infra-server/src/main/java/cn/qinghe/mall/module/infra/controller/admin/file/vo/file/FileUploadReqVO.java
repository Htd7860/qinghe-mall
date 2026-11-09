package cn.qinghe.mall.module.infra.controller.admin.file.vo.file;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** yudao MultipartFile上传VO；用途替代外部自由目录，不接收tenant/owner/public字段。 */
@Data
public class FileUploadReqVO {
    @NotNull
    private MultipartFile file;
    @NotBlank
    @Size(max = 30)
    private String purpose;
}
