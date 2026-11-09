package cn.qinghe.mall.module.promotion.service.seckill;

import cn.qinghe.mall.framework.security.core.LoginUser;
import cn.qinghe.mall.module.promotion.api.seckill.dto.SeckillCheckoutContextDTO;
import cn.qinghe.mall.module.promotion.dal.mysql.seckill.SeckillCheckoutContextMapper;
import cn.qinghe.mall.module.promotion.framework.member.CouponMemberAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 原 validateJoinSeckill 的只读报价资格职责；累计限购/库存最终裁决留给受理事务。 @author HUIHUI */
@Service @RequiredArgsConstructor
public class SeckillCheckoutContextService {
    private final CouponMemberAccess access;
    private final SeckillCheckoutContextMapper contexts;

    public SeckillCheckoutContextDTO current(Long activity,Integer generation,Long sku,Integer quantity) {
        LoginUser member=access.actor();
        if (!id(member.getTenantId()) || !id(activity) || !id(sku) || generation==null || generation<1
                || quantity==null || quantity<1 || quantity>9999) throw exception0(400,"秒杀报价参数不正确");
        SeckillCheckoutContextDTO row=contexts.current(member.getTenantId(),member.getId(),activity,generation,sku);
        if (row==null) throw exception0(409,"活动尚未开放、已停止或发布代次已变化");
        if (row.getPriceCent()==null || row.getPriceCent()<1 || row.getPriceCent()>2000000000
                || row.getSingleLimit()==null || row.getSingleLimit()<1 || row.getMemberLimit()==null
                || row.getMemberLimit()<row.getSingleLimit()) throw exception0(503,"活动报价配置不完整");
        if (quantity>row.getSingleLimit() || quantity>row.getMemberLimit()) throw exception0(400,"购买数量超出活动限额");
        access.actor();return row;
    }
    private boolean id(Long id) { return id!=null && id>0 && id<=9007199254740991L; }
}
