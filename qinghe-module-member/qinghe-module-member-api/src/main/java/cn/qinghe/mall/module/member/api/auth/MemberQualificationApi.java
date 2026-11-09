package cn.qinghe.mall.module.member.api.auth;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.member.api.auth.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import javax.validation.Valid;
/** 择取yudao MemberUserApi的固定Feign账户读取职责；限system资格查询，不开放通用任意会员查询。
 * @author 芋道源码
 */
@FeignClient(name="qinghe-member-qualification",contextId="qingheMemberQualification",url="${qinghe.member-auth.member-base-url:http://127.0.0.1:8084}")
public interface MemberQualificationApi {
    String PATH="/internal-api/member/auth/qualification";
    @PostMapping(PATH)
    CommonResult<MemberQualificationRespDTO> check(@RequestHeader(HttpHeaders.AUTHORIZATION) String serviceAuthorization,
            @Valid @RequestBody MemberQualificationReqDTO body);
}
