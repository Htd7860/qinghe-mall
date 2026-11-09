package cn.qinghe.mall.module.trade.framework.checkout;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.member.api.address.dto.MemberAddressRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="qinghe-checkout-member",contextId="qingheCheckoutMember",url="${qinghe.trade.member-base-url:http://127.0.0.1:8084}",configuration=SystemSecurityFeignConfiguration.class)
public interface CheckoutMemberApi {
    @GetMapping("/internal-api/member/address/current") CommonResult<MemberAddressRespDTO> address(@RequestHeader("Authorization") String bearer,@RequestParam("id") Long id);
}
