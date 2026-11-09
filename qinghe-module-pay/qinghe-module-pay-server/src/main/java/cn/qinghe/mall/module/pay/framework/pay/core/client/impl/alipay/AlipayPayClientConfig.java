package cn.qinghe.mall.module.pay.framework.pay.core.client.impl.alipay;

import javax.validation.ConstraintViolation;
import java.util.Set;
import java.util.Arrays;
import cn.qinghe.mall.module.pay.framework.pay.core.client.PayClientConfig;
import lombok.Data;

import javax.validation.Validator;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 支付宝的 PayClientConfig 实现类
 * 属性主要来自 {@link com.alipay.api.AlipayConfig} 的必要属性
 *
 * @author 芋道源码
 */
@Data
@lombok.ToString(onlyExplicitlyIncluded=true)
public class AlipayPayClientConfig implements PayClientConfig {

    /**
     * 公钥类型 - 公钥模式
     */
    public static final Integer MODE_PUBLIC_KEY = 1;
    /**
     * 公钥类型 - 证书模式
     */
    public static final Integer MODE_CERTIFICATE = 2;

    /**
     * 接口内容加密方式 - AES 加密
     */
    public static final String ENC_TYPE_AES = "AES";

    /**
     * 签名算法类型 - RSA
     */
    public static final String SIGN_TYPE_DEFAULT = "RSA2";

    /**
     * 网关地址
     *
     * 1. <a href="https://openapi.alipay.com/gateway.do">生产环境</a>
     * 2. <a href="https://openapi-sandbox.dl.alipaydev.com/gateway.do">沙箱环境</a>
     */
    @NotBlank(message = "网关地址不能为空", groups = {ModePublicKey.class, ModeCertificate.class})
    private String serverUrl;

    /**
     * 开放平台上创建的应用的 ID
     */
    @NotBlank(message = "开放平台上创建的应用的 ID不能为空", groups = {ModePublicKey.class, ModeCertificate.class})
    private String appId;

    /** Expected receiving merchant; verify after checking notification signature. */
    private String sellerId;

    /**
     * 签名算法类型，推荐：RSA2
     * <p>
     * {@link #SIGN_TYPE_DEFAULT}
     */
    @NotBlank(message = "签名算法类型不能为空", groups = {ModePublicKey.class, ModeCertificate.class})
    private String signType;

    /**
     * 公钥类型
     * 1. {@link #MODE_PUBLIC_KEY} 情况，privateKey + alipayPublicKey
     * 2. {@link #MODE_CERTIFICATE} 情况，appCertContent + alipayPublicCertContent + rootCertContent
     */
    @NotNull(message = "公钥类型不能为空", groups = {ModePublicKey.class, ModeCertificate.class})
    private Integer mode;

    // ========== 公钥模式 ==========
    /**
     * 商户私钥
     */
    @NotBlank(message = "商户私钥不能为空", groups = {ModePublicKey.class})
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String privateKey;

    /**
     * 支付宝公钥字符串
     */
    @NotBlank(message = "支付宝公钥字符串不能为空", groups = {ModePublicKey.class})
    private String alipayPublicKey;

    // ========== 证书模式 ==========
    /**
     * 指定商户公钥应用证书内容字符串
     */
    @NotBlank(message = "指定商户公钥应用证书内容不能为空", groups = {ModeCertificate.class})
    private String appCertContent;
    /**
     * 指定支付宝公钥证书内容字符串
     */
    @NotBlank(message = "指定支付宝公钥证书内容不能为空", groups = {ModeCertificate.class})
    private String alipayPublicCertContent;
    /**
     * 指定根证书内容字符串
     */
    @NotBlank(message = "指定根证书内容字符串不能为空", groups = {ModeCertificate.class})
    private String rootCertContent;

    /**
     * 接口内容加密方式
     *
     * 1. 如果为空，将使用无加密方式
     * 2. 如果要加密，目前支付宝只有 AES 一种加密方式
     *
     * @see <a href="https://opendocs.alipay.com/common/02mse3">支付宝开放平台</a>
     * @see AlipayPayClientConfig#ENC_TYPE_AES
     */
    private String encryptType;

    /**
     * 接口内容加密的私钥
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String encryptKey;

    public interface ModePublicKey {
    }

    public interface ModeCertificate {
    }

    @Override
    public void validate(Validator validator) {
        if (!Arrays.asList(MODE_PUBLIC_KEY, MODE_CERTIFICATE).contains(mode)) throw new IllegalArgumentException("支付公钥类型不正确");
        Set<ConstraintViolation<AlipayPayClientConfig>> violations=validator.validate(this,
                MODE_PUBLIC_KEY.equals(mode)?ModePublicKey.class:ModeCertificate.class);
        if (!violations.isEmpty()) throw new IllegalArgumentException("支付宝渠道配置不完整");
        if (!SIGN_TYPE_DEFAULT.equals(signType) || appId==null || !appId.matches("[0-9]{16}")
                || sellerId==null || !sellerId.matches("[0-9]{16}")) throw new IllegalArgumentException("支付宝应用/收款方/RSA2配置不正确");
        if (!Arrays.asList("https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                "https://openapi.alipaydev.com/gateway.do").contains(serverUrl))
            throw new IllegalArgumentException("当前客户端仅开放支付宝HTTPS沙箱，真实资金渠道另行验收");
        if ((encryptType!=null && !encryptType.isEmpty()) || (encryptKey!=null && !encryptKey.isEmpty()))
            throw new IllegalArgumentException("本批未启用接口内容AES加密配置");
    }

}
