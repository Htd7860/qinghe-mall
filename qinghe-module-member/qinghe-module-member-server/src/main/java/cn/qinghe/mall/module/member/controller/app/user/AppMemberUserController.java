package cn.qinghe.mall.module.member.controller.app.user;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.module.member.controller.app.user.vo.*;
import cn.qinghe.mall.module.member.service.auth.MemberSessionService;
import cn.qinghe.mall.module.member.service.user.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
/** 择取原AppMemberUserController.getUserInfo/updateUser；仅当前会员，不开放任意userId。
 * @author 芋道源码
 */
@RestController
@RequestMapping("/app-api/member/user")
@RequiredArgsConstructor
public class AppMemberUserController {
    private final MemberProfileService profiles;
    private final MemberSessionService members;
    @ModelAttribute
    public void prepare(HttpServletRequest request,HttpServletResponse response,@RequestParam(value="store",required=false) String store) {
        response.setHeader("Cache-Control","no-store");members.current(request,store);
    }
    @GetMapping("/get")
    public CommonResult<AppMemberProfileRespVO> getUserInfo() { return CommonResult.success(profiles.current()); }
    @PutMapping("/preferences")
    public CommonResult<AppMemberProfileRespVO> updatePreference(@Valid @RequestBody AppMemberPreferenceReqVO body) {
        return CommonResult.success(profiles.updatePreference(body.getMarketingOptOut(),body.getExpectedVersion()));
    }
}
