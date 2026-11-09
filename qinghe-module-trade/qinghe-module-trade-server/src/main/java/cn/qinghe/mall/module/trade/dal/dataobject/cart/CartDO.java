package cn.qinghe.mall.module.trade.dal.dataobject.cart;

import cn.qinghe.mall.framework.tenant.core.db.TenantBaseDo;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 购物车的商品信息 DO
 *
 * 每个商品，对应一条记录，通过 {@link #spuId} 和 {@link #skuId} 关联
 *
 * @author 芋道源码
 */
@TableName("trade_cart")
@Data
public class CartDO extends TenantBaseDo {

    /** 行版本供后续订单清理核对；浏览器写入还须整个购物车版本。 */
    private Long authVersion;

    // ========= 基础字段 BEGIN =========

    /**
     * 编号，唯一自增
     */
    @TableId(type=IdType.AUTO)
    private Long id;

    /**
     * 用户编号
     *
     * 关联 MemberUserDO 的 id 编号
     */
    private Long userId;

    // ========= 商品信息 =========

    /**
     * 商品 SPU 编号
     *
     * 关联 ProductSpuDO 的 id 编号
     */
    private Long spuId;
    /**
     * 商品 SKU 编号
     *
     * 关联 ProductSkuDO 的 id 编号
     */
    private Long skuId;
    /**
     * 商品购买数量
     */
    private Integer count;
    /**
     * 是否选中
     */
    private Boolean selected;

}
