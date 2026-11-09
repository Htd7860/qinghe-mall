package cn.qinghe.mall.module.pay.framework.pay.core.client;
import javax.validation.Validator;
/** 原PayClientConfig校验职责；当前仅一个具体渠道，不启用@class任意类反序列化。
 * @author 芋道源码
 */
public interface PayClientConfig { void validate(Validator validator); }
