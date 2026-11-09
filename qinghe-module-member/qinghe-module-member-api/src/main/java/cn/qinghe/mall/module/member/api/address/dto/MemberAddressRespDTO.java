package cn.qinghe.mall.module.member.api.address.dto;

import lombok.Data;

/** 择取 yudao 地址快照字段；内部结算 RPC 仅返回当前已认证会员本人的地址。 */
@Data
@lombok.ToString(onlyExplicitlyIncluded=true)
public class MemberAddressRespDTO {

    private Long id;

    private Long userId;

    private String name;

    private String mobile;

    private Integer areaId;

    private String detailAddress;

    private Boolean defaultStatus;

    private Long tenantId;
    private Long authVersion;

}
