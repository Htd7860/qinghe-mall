package cn.qinghe.mall.module.promotion.controller.app.coupon.vo;
import lombok.Data;
/** found=false只表示此刻未读到成功回执，不代表另一请求未在执行；重试沿用原请求号。 */
@Data
public class AppCouponGrantRespVO {
    private Boolean found;
    private Long couponId;
    private Long templateId;
}
