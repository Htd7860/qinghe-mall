package cn.qinghe.mall.module.member.controller.internal.address;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils;
import cn.qinghe.mall.module.member.api.address.dto.MemberAddressRespDTO;
import cn.qinghe.mall.module.member.dal.dataobject.address.MemberAddressDO;
import cn.qinghe.mall.module.member.service.address.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.*;
/** 原AddressApi.getAddress的本人适配；既有Service再次核对真实MEMBER/凭据代次/tenant。
 * @author 芋道源码
 */
@RestController @Validated @RequiredArgsConstructor
public class InternalCheckoutAddressController {
    private final AddressService addresses;
    @GetMapping("/internal-api/member/address/current")
    public CommonResult<MemberAddressRespDTO> get(@RequestParam @Min(1) Long id,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        MemberAddressDO row=addresses.getAddress(SecurityFrameworkUtils.getLoginUserId(),id);
        return row==null?CommonResult.error(404,"收货地址不存在"):CommonResult.success(BeanUtils.toBean(row,MemberAddressRespDTO.class));
    }
}
