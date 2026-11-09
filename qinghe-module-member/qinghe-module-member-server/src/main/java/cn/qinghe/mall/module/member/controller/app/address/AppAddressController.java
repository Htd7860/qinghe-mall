package cn.qinghe.mall.module.member.controller.app.address;
import cn.qinghe.mall.framework.common.pojo.CommonResult;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.common.util.area.Area;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.module.member.controller.app.address.vo.*;
import cn.qinghe.mall.module.member.dal.dataobject.address.MemberAddressDO;
import cn.qinghe.mall.module.member.service.address.AddressService;
import cn.qinghe.mall.module.member.service.auth.MemberSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.*;
import java.util.stream.Collectors;
import static cn.qinghe.mall.framework.common.pojo.CommonResult.success;
import static cn.qinghe.mall.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;
/** 择取yudao六个本人地址入口；所有权在Service再次核对，不接受外部userId。
 * @author 芋道源码
 */
@Tag(name="用户APP - 收货地址")
@RestController
@RequestMapping("/app-api/member/address")
@RequiredArgsConstructor
@Validated
public class AppAddressController {
    private final AddressService addressService;
    private final MemberSessionService members;
    @ModelAttribute
    public void prepare(HttpServletRequest request,HttpServletResponse response,
            @RequestParam(value="store",required=false) @Pattern(regexp="[a-z0-9-]{3,32}") String store) {
        response.setHeader("Cache-Control","no-store");
        members.current(request,store); // RPC在地址事务外，核对实际Host和已认证会员店铺。
    }
    @GetMapping("/area-options")
    public CommonResult<List<AppAddressAreaRespVO>> areaOptions() {
        return success(AreaUtils.getChinaRegions().stream().map(area -> {
            AppAddressAreaRespVO option=new AppAddressAreaRespVO();option.setId(area.getId());option.setName(area.getName());option.setType(area.getType());
            option.setParentId(area.getParent()!=null && AreaUtils.isChinaRegion(area.getParent().getId())?area.getParent().getId():0);
            return option;
        }).collect(Collectors.toList()));
    }
    @PostMapping("/create") @Operation(summary="创建本人收货地址")
    public CommonResult<Long> createAddress(@Valid @RequestBody AppAddressCreateReqVO body) { return success(addressService.createAddress(getLoginUserId(),body)); }
    @PutMapping("/update") @Operation(summary="修改本人收货地址")
    public CommonResult<Boolean> updateAddress(@Valid @RequestBody AppAddressUpdateReqVO body) { addressService.updateAddress(getLoginUserId(),body);return success(true); }
    @DeleteMapping("/delete") @Operation(summary="删除本人收货地址")
    public CommonResult<Boolean> deleteAddress(@RequestParam("id") @Min(1) Long id,@RequestParam("expectedVersion") @Min(0) Long version) {
        addressService.deleteAddress(getLoginUserId(),id,version);return success(true);
    }
    @GetMapping("/get") @Operation(summary="获取本人收货地址")
    public CommonResult<AppAddressRespVO> getAddress(@RequestParam("id") @Min(1) Long id) {
        MemberAddressDO address=addressService.getAddress(getLoginUserId(),id);
        if (address==null) { throw exception0(404,"收货地址不存在"); }return success(response(address));
    }
    @GetMapping("/list") @Operation(summary="获取本人收货地址列表")
    public CommonResult<List<AppAddressRespVO>> getAddressList() {
        return success(addressService.getAddressList(getLoginUserId()).stream().map(this::response).collect(Collectors.toList()));
    }
    @GetMapping("/get-default") @Operation(summary="获取本人默认收货地址")
    public CommonResult<AppAddressRespVO> getDefaultUserAddress() { return success(response(addressService.getDefaultUserAddress(getLoginUserId()))); }
    private AppAddressRespVO response(MemberAddressDO address) {
        if (address==null) { return null; }
        AppAddressRespVO result=BeanUtils.toBean(address,AppAddressRespVO.class);
        Area area=AreaUtils.getArea(address.getAreaId().intValue());List<String> names=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        while (area!=null && AreaUtils.isChinaRegion(area.getId()) && seen.add(area.getId())) { names.add(0,area.getName());area=area.getParent(); }
        result.setAreaName(String.join(" / ",names));return result;
    }
}
