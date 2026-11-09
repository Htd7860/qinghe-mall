package cn.qinghe.mall.module.member.controller.app.auth;

import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.member.controller.app.auth.vo.AppAuthSessionRespVO;
import cn.qinghe.mall.module.member.service.auth.MemberSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Pattern;

/** 原会员资料查询委托，当前用户编号只从已验证的SecurityContext取得。
 * @author 芋道源码
 */
@RestController
@Validated
@RequiredArgsConstructor
public class AppAuthSessionController {
    private final MemberSessionService sessions;
    @GetMapping("/app-api/auth/session")
    public CommonResult<AppAuthSessionRespVO> current(
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store,
            HttpServletRequest request,HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        return CommonResult.success(sessions.current(request,store));
    }
}
