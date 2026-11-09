package cn.qinghe.mall.module.trade.framework.member;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.security.core.rpc.SystemSecurityFeignConfiguration;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontReqDTO;
import cn.qinghe.mall.module.system.api.tenant.dto.MemberStorefrontRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="qinghe-trade-storefront",contextId="qingheTradeStorefront",
    url="${qinghe.security.remote.system-base-url:http://127.0.0.1:8080}",configuration=SystemSecurityFeignConfiguration.class)
public interface TradeStorefrontApi {
    @PostMapping("/internal-api/auth/storefront/current")
    CommonResult<MemberStorefrontRespDTO> current(@RequestHeader("Authorization") String bearer,@RequestBody MemberStorefrontReqDTO body);
}
