package cn.qinghe.mall.module.member.service.address;

import cn.qinghe.mall.framework.common.util.area.Area;
import cn.qinghe.mall.framework.common.util.area.AreaUtils;
import cn.qinghe.mall.framework.common.util.object.BeanUtils;
import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import cn.qinghe.mall.module.member.controller.app.address.vo.*;
import cn.qinghe.mall.module.member.dal.dataobject.address.MemberAddressDO;
import cn.qinghe.mall.module.member.dal.dataobject.user.MemberUserDO;
import cn.qinghe.mall.module.member.dal.mysql.address.MemberAddressMapper;
import cn.qinghe.mall.module.member.framework.address.MemberAddressProperties;
import cn.qinghe.mall.module.member.service.auth.MemberSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import javax.validation.Validator;
import java.time.LocalDateTime;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 直接择取yudao AddressServiceImpl六方法：本人查询/默认切换/地址CRUD。
 * 租户、MEMBER类型/真实账号/凭据代次、父会员锁、CAS与唯一默认约束为当前项目必要适配。
 * @author 芋道源码
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {
    private final MemberAddressMapper memberAddressMapper;
    private final MemberSessionService members;
    private final MemberAddressProperties settings;
    private final Validator validator;

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=10)
    public Long createAddress(Long userId,AppAddressCreateReqVO body) {
        validateInput(body);MemberUserDO user=requireMember(userId,true);
        if (memberAddressMapper.countByUserId(userId)>=settings.getMaxAddresses()) {
            throw exception0(400,"收货地址已达到数量上限");
        }
        if (Boolean.TRUE.equals(body.getDefaultStatus())) { clearPreviousDefault(userId,null); }
        MemberAddressDO address=metadata(body);address.setTenantId(user.getTenantId());address.setUserId(userId);address.setAuthVersion(0L);
        address.setCreator(userId.toString());address.setUpdater(userId.toString());address.setDeleted(false);
        address.setCreateTime(LocalDateTime.now());address.setUpdateTime(address.getCreateTime());
        if (memberAddressMapper.insert(address)!=1 || address.getId()==null) { throw new IllegalStateException("收货地址保存失败"); }
        audit(userId,address.getId(),"CREATE",-1L,0L);return address.getId();
    }

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=10)
    public void updateAddress(Long userId,AppAddressUpdateReqVO body) {
        validateInput(body);MemberUserDO user=requireMember(userId,true);
        MemberAddressDO current=requiredAddress(userId,body.getId());requireVersion(current,body.getExpectedVersion());
        if (Boolean.TRUE.equals(body.getDefaultStatus())) { clearPreviousDefault(userId,current.getId()); }
        if (memberAddressMapper.updateOwned(user.getTenantId(),userId,current.getId(),current.getAuthVersion(),metadata(body))!=1) {
            throw exception0(409,"收货地址已变化，请重新读取");
        }
        audit(userId,current.getId(),"UPDATE",current.getAuthVersion(),current.getAuthVersion()+1);
    }

    @Override
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED,timeout=10)
    public void deleteAddress(Long userId,Long id,Long expectedVersion) {
        MemberUserDO user=requireMember(userId,true);MemberAddressDO current=requiredAddress(userId,id);requireVersion(current,expectedVersion);
        if (memberAddressMapper.deleteOwned(user.getTenantId(),userId,id,current.getAuthVersion())!=1) { throw exception0(409,"收货地址已变化，请重新读取"); }
        audit(userId,id,"DELETE",current.getAuthVersion(),current.getAuthVersion()+1);
        // 原版删除语义保留：删默认地址后没有默认，不擅自替用户选另一个地址。
    }

    @Override
    public MemberAddressDO getAddress(Long userId,Long id) {
        requireMember(userId,false);
        if (id==null || id<=0) { throw exception0(400,"地址编号不正确"); }
        return memberAddressMapper.selectByIdAndUserId(id,userId);
    }
    @Override
    public List<MemberAddressDO> getAddressList(Long userId) {
        requireMember(userId,false);return memberAddressMapper.selectListByUserIdAndDefaulted(userId,null);
    }
    @Override
    public MemberAddressDO getDefaultUserAddress(Long userId) {
        requireMember(userId,false);List<MemberAddressDO> addresses=memberAddressMapper.selectListByUserIdAndDefaulted(userId,true);
        if (addresses.size()>1) { throw new IllegalStateException("默认收货地址数据异常"); }
        return addresses.isEmpty()?null:addresses.get(0);
    }

    private MemberUserDO requireMember(Long userId,boolean lock) {
        MemberUserDO user=members.requireCurrentMember(lock);
        if (!Objects.equals(userId,user.getId())) { throw exception0(403,"收货地址入口与会员身份不匹配"); }
        return user;
    }
    private MemberAddressDO requiredAddress(Long userId,Long id) {
        if (id==null || id<=0) { throw exception0(400,"地址编号不正确"); }
        MemberAddressDO address=memberAddressMapper.selectByIdAndUserId(id,userId);
        if (address==null) { throw exception0(404,"收货地址不存在"); }return address;
    }
    private void clearPreviousDefault(Long userId,Long except) {
        for (MemberAddressDO address:memberAddressMapper.selectListByUserIdAndDefaulted(userId,true)) {
            if (Objects.equals(address.getId(),except)) { continue; }
            requireVersion(address,address.getAuthVersion());
            if (memberAddressMapper.clearDefault(TenantContextHolder.getRequiredTenantId(),userId,address.getId(),address.getAuthVersion())!=1) {
                throw exception0(409,"默认地址已变化，请重新读取");
            }
            audit(userId,address.getId(),"DEFAULT_CLEAR",address.getAuthVersion(),address.getAuthVersion()+1);
        }
    }
    private void requireVersion(MemberAddressDO row,Long expected) {
        if (expected==null || expected<0) { throw exception0(400,"请提供当前地址版本"); }
        if (!Objects.equals(row.getAuthVersion(),expected) || expected==Long.MAX_VALUE) { throw exception0(409,"地址已变化或版本达到上限，请重新读取"); }
    }
    private void validateInput(AppAddressBaseVO body) {
        if (body==null || !validator.validate(body).isEmpty() || body.getName().trim().isEmpty() || body.getDetailAddress().trim().isEmpty()) {
            throw exception0(400,"收货地址字段不完整或格式不正确");
        }
        Area area=AreaUtils.getArea(body.getAreaId().intValue());
        if (area==null || !AreaUtils.isChinaRegion(area.getId()) || area.getType()<3 || !area.getChildren().isEmpty()) {
            throw exception0(400,"请选择有效的末级收货地区");
        }
    }
    private MemberAddressDO metadata(AppAddressBaseVO body) {
        MemberAddressDO row=BeanUtils.toBean(body,MemberAddressDO.class);
        row.setId(null);row.setName(body.getName().trim());row.setDetailAddress(body.getDetailAddress().trim());return row;
    }
    private void audit(Long userId,Long id,String action,Long from,Long to) {
        if (memberAddressMapper.audit(TenantContextHolder.getRequiredTenantId(),userId,id,action,from,to)!=1) {
            throw new IllegalStateException("地址变更记录保存失败");
        }
    }
}
