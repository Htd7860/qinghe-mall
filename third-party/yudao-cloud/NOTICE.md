# yudao-cloud 源码复用记录

来源：本地 `D:/aaa/a-yudao/yudao-cloud`，原项目 https://github.com/YunaiV/yudao-cloud。

Copyright (c) 2021 yudao-cloud。原 MIT 许可完整保存在同目录 LICENSE；保留源码中的作者声明。

2026-10-28 起按用户 DEC-065 授权择取适用代码，调整到 cn.qinghe.mall 包及已有依赖；不修改源目录。

首批：infra 文件客户端接口、配置、抽象客户端、工厂、本地客户端、路径工具及存储类型枚举。
另择取 infra 文件错误码与 common HttpUtils.encodeUrlPath 方法；路径编码放入文件工具，不导入整个上游工具依赖链。
存储枚举本批只保留 LOCAL；抽象客户端日志仅记录配置编号。原文与移植后源码 SHA-256、差异与验证边界在正式工作区
`mall-saas/docs/evidence/yudao-infra-file-storage-port.json`。

本记录只声明实际移植范围，不表示 HTTP 上传、权限隔离、文件持久化或运行验收已经完成。

第二批：依据 FileDO/FileConfigDO、FileMapper/FileConfigMapper、FilePageReqVO、FileService/Impl、
FileConfigService/Impl 适配文件元数据和服务；FileTypeUtils 的 Tika 类型识别方法直接移植。
新增租户/上传者/用途限制，缩小图片类型范围，路径改为租户/用途/日期/UUID；文件配置目前只读LOCAL。
源码映射与双向摘要：mall-saas/docs/evidence/yudao-infra-file-service-port.json。
新库/两表及主配置已创建，Java文件读写和JSON映射未运行验证，HTTP上传仍待实现。

第三批：FileController及上传/响应VO依据yudao同名类移植适配，公开图片URL改为public-api固定路径结构。
内部权限RPC采用原版PermissionApi的Feign声明形式，身份按本项目Bearer协议适配；没有复制信任login-user头的机制。
前端复用当前项目组件与样式接真实文件HTTP，未复制不兼容的ElementPlus页面。
本片段修改AbstractFileClient公共URL前缀，历史证据的摘要不代表修改后的当前文件；后续摘要见mall-saas/docs/evidence/yudao-infra-http-port.json。
文件HTTP、认证RPC和前端尚未编译或运行验证。

商品基础批次：直接择取product分类/品牌/属性/属性值DO、VO和Mapper，以及SPU/SKU DO、SPU状态枚举、核心错误码。
BeanUtils复用原版Hutool实现，BaseMapperX只增加本批所需原版查询重载；不覆盖已有分页逻辑。
必要差异为TenantBaseDo、MySQL AUTO、分类最大三级、销售/展示属性和启停、资料版本、Decimal计量字段；移除范围外积分与分销字段。
详见mall-saas/docs/design/PRODUCT-CATALOG-REUSE.md及docs/evidence/yudao-product-*-storage-port.json。
六张商品表已创建但仍无业务数据；Service/HTTP/发布/库存及实际运行验收未完成。

后续商品Service批次：ProductCategory/Brand/Property/PropertyValue的Service接口与实现依据原版移植，
ProductPropertyReferenceService参考原SKU服务的updateSkuProperty/updateSkuPropertyValue，只同步名称相关JSON。
差异为三级树与真实叶子、同租户引用、版本/审计、低频租户配置锁及经营资格复查；HTTP和发布/库存仍未实现。
来源和当前摘要见mall-saas/docs/evidence/yudao-product-catalog-services-port.json。

商品HTTP批次：ProductCategoryController、ProductBrandController、ProductPropertyController及ProductPropertyValueController直接择取原类，必要包名/前缀/BeanUtils与版本/显式权限适配；源文件和目标摘要见mall-saas/docs/evidence/yudao-product-catalog-http-port.json。独立应用配置及文件引用保护为本项目接线增量；当前前端沿用既有组件，未复制ElementPlus页面。旧批次“尚无HTTP”是历史状态，本批源码已接，尚未编译或运行验收。

SPU/SKU校验批次：择取ProductSkuSaveReqVO、ProductSpuSaveReqVO的范围内输入字段，ProductSkuConvert.buildPropertyKey以及ProductSkuService/Impl的规格校验和只读查询。保留原作者，删除范围外积分/分销和不安全外部统计输入，加入真实属性值归属/销售展示类型/配置上限/金额计量边界与规范组合摘要。尚未移植SKU库存写入、删除或SPU发布方法；不以类名相同声称原类全部功能完成。源映射与当前摘要见mall-saas/docs/evidence/yudao-product-sku-validation-port.json。

SKU差集与库存批次：以ProductSkuServiceImpl.createSkuList/updateSkuList/updateSkuStock及ProductSkuMapper的增减库存方法择取流程，保留作者。适配稳定SKU id、遗漏停用、元数据白名单、版本/审计与真实图片/范围检查；B04要求的初始/手工幂等库存账本为本项目增量，不移植原版库存变化直接修改销量的逻辑。内部数据范围RPC沿用原PermissionApi分工，计算直接复用本项目已完成的UserDataScope，不复制按外部userId查询的信任边界。当前不含订单/退款/秒杀库存协议，未编译或运行。映射与摘要见mall-saas/docs/evidence/yudao-product-sku-stock-write-port.json。

SPU与配送前置批次：择取ProductSpuService/Impl的create/update/get流程，保留作者，默认下架及数据范围/真实模板图片/审计为当前确认规则适配。择取trade配送三DO、三Mapper、按件重量枚举与validateDeliveryExpressTemplate及主从读取，暂不移植原版模板CRUD/订单核价；Decimal计量、显式默认规则、无重叠区域和启停/版本为B05适配。另从biz-ip择取Area/AreaTypeEnum、AreaUtils.init/getArea并原样保留area.csv（重命名qinghe-area.csv），不引入IP定位；地区数据只代表本地原版快照。来源与双向摘要见mall-saas/docs/evidence/yudao-product-spu-delivery-save-port.json。

配送管理/核价片段：直接择取DeliveryExpressTemplateController及9份VO、模板主从CRUD流程；择取TradeDeliveryPriceCalculator按模板聚合/首续费/包邮/分摊职责，保留jason作者。适配Decimal、地区缺失拒绝、重量缺失拒绝、按真实件数包邮、折后同模板金额及确定性余数分摊，输出费率快照；不复制未实现的会员地址/自提/全局包邮或返回0占位。前端沿用本项目组件与样式，不导入原ElementPlus页面；源映射与当前摘要见mall-saas/docs/evidence/yudao-delivery-management-calculator-port.json。

商品管理批次：择取ProductSpuController七方法、Page/Status/SPU/SKU四VO与Service状态/回收站删除/计数和Mapper tab条件；未移植Excel及未接消费者字段。当前增加真实发布重验、状态CAS、SQL数据范围、保留历史SKU的逻辑删除和手工库存HTTP/原结果恢复；这些为已确认B03/B04适配。后台页面沿用本项目Vue组件，原Commerce只在显式demo分支保留。来源与摘要见mall-saas/docs/evidence/yudao-product-spu-management-port.json。


商品归属转移片段：yudao商品模块无对应转移方法，这是已确认B03需求的本项目增量；复用现有商品共同事务/数据范围/CAS/审计。对照AdminUserApiImpl.getUser的真实人员DTO投影职责，使用本项目既有AdminUserMapper/UserDataScope实现受限最小人员投影；不称原版转移方法被复制。范围及摘要见mall-saas/docs/evidence/yudao-product-owner-transfer-port.json。


商品导出片段：直接提取ExcelUtils.write写出链和ColumnWidthMatchStyleStrategy（保留芋道源码/hmb作者），包名调整，列宽字节数显式UTF-8；新增qinghe-spring-boot-starter-excel，FastExcel1.3.0取本地原版BOM。择取ProductSpuController.exportSpuList入口/权限/筛选/Excel职责，PAGE_SIZE_NONE改已确认的有界游标读取和生成记录。未复制未使用Excel导入/下拉/字典链，未称原有审计注解已移植。来源和摘要见mall-saas/docs/evidence/yudao-product-export-port.json。


会员地址片段：择取MemberUserDO的账号/基础资料、MemberAddressDO/Mapper、AddressService/Impl六方法、AppAddressController六方法及四VO、MemberAddressRespDTO，保留原作者。原地址BaseDO改TenantBaseDo、MySQL AUTO；补当前MEMBER/租户/凭据检查、父会员锁/版本/唯一默认及不含PII审计，范围外积分等级经验链不引入。原本地地址DTO的Schema描述有乱码，目标去掉损坏描述。未移植消费者登录/SMS/真实MEMBER策略、公开Gateway路由或前端。来源及摘要见mall-saas/docs/evidence/yudao-member-address-port.json。


消费者入口片段：择取TenantController.getTenantByWebsite的入口定位/真实租户/最小公开投影职责，沿用既有TenantService/Mapper；websites集合按已确认B02改system_tenant_entry唯一表，生产验证/受限本地编码定位/Host来源等为项目适配。未复制公开租户全列表，未声称原版已实现独立域名验证生命周期。来源/摘要见mall-saas/docs/evidence/yudao-storefront-resolution-port.json。


真实MEMBER资格片段：择取MemberUserApi/Impl中Feign账户读取/getUser/validateUser职责，保留作者并改为最小资格DTO；没有复制任意会员列表/手机号查询。专用服务Bearer只读边界用于避免system/member用户校验递归，是已确认隔离要求的项目增量，不冒称原版存在相同过滤器。资格接现有EnumMap和TenantService，ADMIN代码不变。来源/摘要见mall-saas/docs/evidence/yudao-member-qualification-port.json。


会员密码/短信渠道片段：择取MemberAuthServiceImpl.login0与MemberUserServiceImpl.isPasswordMatch（芋道源码），保留真实手机号/密码匹配/启用检查；增加当前UTF-8边界及二次读取。直接提取AliyunSmsClient.sendSms/request/percentCode（zzf），原canonical签名方法仅替换属性字段和受控post调用；默认禁用、隔离测试收件箱与有界HTTP为项目适配，未引入打印SmsChannelProperties的init/refresh日志。原SMS验证码账本/配额/消费流程尚未移植。来源摘要见mall-saas/docs/evidence/yudao-member-password-sms-channel-port.json。


验证码记录片段：择取SmsCodeService/Impl.create/send/validate/use、SmsCodeDO/Mapper/Properties（芋道源码），补tenant号码锁、持久额度、编码代明文、渠道未知状态、错误次数和本地变更回调事务。新增复杂度用于已确认隔离/限额/一次性与发送超时边界，不声称原版已具有相同实现。没有公开短信或注册入口，没有实际生成发送验证码；来源摘要见mall-saas/docs/evidence/yudao-member-sms-code-port.json。

会员注册/找回片段：择取原MemberUserService/Impl.createUserIfAbsent/createUser、resetUserPassword、encodePassword（芋道源码），保留原作者；昵称/启用/注册资料/insert及验证码后编码更新流程适配，显式注册密码、租户身份保留/本地消费事务/版本及审计为项目增量。H5终端值20取原TerminalEnum，不复制其他终端业务；不移植社交/积分等级MQ依赖。来源摘要见mall-saas/docs/evidence/yudao-member-account-mutation-port.json。

system侧MEMBER签发片段：择取原OAuth2TokenServiceImpl.createAccessToken/createOAuth2RefreshToken/createOAuth2AccessToken（芋道源码）的两表/归属流程，以及OAuth2TokenApiImpl.createAccessToken委托；现有用户框架生成器和事务模式保留。固定消费者身份、真实会员资格与代次、独立服务凭据/最小DTO为项目适配，不冒称原版已有相同服务权限或分布式原子性。来源摘要见mall-saas/docs/evidence/yudao-member-token-issue-port.json。

会员密码到签发片段：择取原MemberAuthServiceImpl.login/login0/createTokenAfterLoginSuccess（芋道源码）的本地密码认证后调用OAuth2 API顺序，直接复用当前密码与签发实现，不移植社交绑定/短信自动开户；固定服务凭据、最小候选、客户端隔离与响应核对为本项目适配。原登录日志仍待后续，没有以本批方法返回声称完整登录完成。来源摘要见mall-saas/docs/evidence/yudao-member-login-issue-port.json。

会员认证店铺核对片段：择取原AppTenantController.getTenantByWebsite（芋道源码）入口定位/最小投影职责，直接复用当前StorefrontService，不复制第二套查询规则；实际Host提取、专用服务POST和返回前归属版本复查为已确认隔离边界适配。没有复制原公开website参数/PermitAll/TenantIgnore到内部入口。来源摘要见mall-saas/docs/evidence/yudao-member-storefront-auth-port.json。

会员登录保护片段：继续围绕芋道MemberAuthServiceImpl.login0的真实认证拒绝接线，但本批限额并非原版另行移植：直接提取当前项目已有LoginFailureLimiter与原样Lua供system/member复用，避免第二份算法。后台适配/历史Key保持，内部HTTP业务错误分离是项目边界修复；原版登录记录职责仍待下一片段。证据见mall-saas/docs/evidence/login-failure-limiter-extraction.json及yudao-member-login-protection-port.json。

会员登录记录片段：择取原LoginLogDO/Mapper/ServiceImpl（芋道源码）的身份/标识/结果/时刻与insert职责，以及MemberAuthServiceImpl.createLoginLog/updateUserLogin流程；不复制原手机号/完整UA日志、跨类型查询/导出或system日志RPC。当前租户/摘要、真实会员锁/代次、member本地事务和失败记录处理为项目适配，不承诺与system令牌跨库原子性。来源摘要见mall-saas/docs/evidence/yudao-member-login-history-port.json。

消费者密码登录/身份HTTP片段：择取原AppAuthController.login（芋道源码）的JSON/PermitAll/Service委托及AppMemberUserController.getUserInfo按当前登录ID重读职责；复用当前后台浏览器TokenPair/Identity形状，实际Host/租户/会员代次/最小资料/429及精确网关为项目适配。没有复制社交/短信自动登录或等级积分。来源摘要见mall-saas/docs/evidence/yudao-member-login-session-http-port.json。

system会员刷新退出片段：择取原OAuth2TokenServiceImpl.refreshAccessToken/removeAccessToken（芋道源码）父会话/client/绝对期限/替换access与清理职责；直接复用当前OAuth2TokenRevokeServiceImpl以及后台已确认父子锁/批量revokedTime/提交后删缓存模式，不复制另一个通用撤销器，不套用原物理删除与事务内Redis语义。固定真实MEMBER/代次为项目适配，来源见mall-saas/docs/evidence/yudao-member-token-lifecycle-system-port.json。


消费者刷新退出HTTP片段：择取原AppAuthController.refreshToken/logout及MemberAuthServiceImpl同名方法（芋道源码）的精确POST/PermitAll/凭据提取和OAuth2 API委托，保留作者。复用当前内部MEMBER生命周期与现有前端会话，JSON write-only参数、固定服务调用/品牌店铺重读、过期access清理与精确网关为项目适配；不声称已移植原登出业务日志或已运行。来源摘要见mall-saas/docs/evidence/yudao-member-token-lifecycle-http-port.json。


消费者账号公开流程：择取AppAuthController.sendSmsCode、AppMemberUserController.resetUserPassword与AppAuthSmsSendReqVO/AppMemberUserResetPasswordReqVO（芋道源码）的薄委托/校验字段，保留作者。当前显式注册、固定两种用途、JSON/auth路径、真实店铺与现有事务包装、Boolean结果/不自动登录及前端表单为本项目适配；不复制短信自动开户或任意scene/模板入口。来源摘要见mall-saas/docs/evidence/yudao-member-account-http-port.json。


会员本人地址/资料/偏好页面片段：继续直接复用原AppAddressController/AddressServiceImpl本人CRUD与默认切换（芋道源码），原getUserInfo/updateUser与AppMemberUserInfoRespVO职责按当前本人最小资料适配。营销退出白名单/版本审计为B10项目增量，非原版同名能力；不复制积分等级、手机号变更或批量隐私导出。地区选项复用已移植AreaUtils/CSV，界面为当前设计体系适配，不冒称原版H5界面整套移植。来源摘要见mall-saas/docs/evidence/yudao-member-address-profile-http-port.json。


优惠券领域片段：直接择取PromotionDiscountTypeEnum/PromotionProductScopeEnum/CouponTakeTypeEnum/CouponStatusEnum/CouponTemplateValidityTypeEnum、CouponTemplateDO/CouponDO、CouponTemplateBaseVO（芋道源码），并择取两Mapper、模板Service接口/实现、CouponService发放和CouponConvert快照流程。保留作者与MIT，未选REGISTER自动送券及积分等级等依赖。可信租户/JSON/MySQL/版本、CAS审计、真实商品范围当前Bearer校验、成功额度不返还和稳定键/RC事务是B05等已确认需求适配；订单占用/作废状态仅数据预留，不冒称原版具备本项目完整恢复协议。来源与双向摘要见mall-saas/docs/evidence/yudao-promotion-coupon-core-port.json。


商家优惠券管理HTTP片段：直接择取原CouponTemplateController六个CRUD/分页委托与CouponTemplateCreateReqVO/UpdateReqVO/UpdateStatusReqVO/RespVO职责（芋道源码），保留作者。当前/admin-api/no-store/端权限/CAS/最小响应为适配；响应不含未接通的核销统计展示。页面复用本项目真实管理组件与商品查询，不宣称直接复制原版整套UI。来源摘要见mall-saas/docs/evidence/yudao-coupon-template-admin-http-port.json。


2026-10-29 Consumer coupon slice: selected AppCouponController.takeCoupon/getCouponPage/getCoupon, AppCouponTemplateController.getCouponTemplatePage, AppCouponTakeReqVO/AppCouponPageReqVO/AppCouponRespVO/AppCouponTemplateRespVO, CouponServiceImpl.getTakeCountMapByTemplateIds/getUserCanCanTakeMap and CouponMapper grouped count duties from yudao-module-mall/yudao-module-promotion-server. Original author 芋道源码 and MIT LICENSE retained. Adapted app prefix, current member/storefront admission, shared minimal face projection, existing RC grant core and successful stable-key receipt recovery; consumer page adapted to this project's existing Vue UI. No claim of runtime acceptance. Source/target hashes: mall-saas/docs/evidence/yudao-coupon-member-http-port.json.


2026-10-29 Coupon order/pricing domain: directly copied CouponRespDTO from yudao-module-promotion-api (namespace, Long templateId, tenant/version adaptation). Selected TradeCouponPriceCalculator.calculate/calculateCoupons/getCouponPrice/filterMatchCouponOrderItems and TradePriceCalculatorHelper.dividePrice duties, and CouponServiceImpl.useCoupon/returnUsedCoupon/invalidate plus CouponMapper conditional update duties. Original author 芋道源码 and MIT LICENSE retained. Project B05/B06 differences: exact long cents, stable largest remainder and unit allocation, reservation/order permanent result, version/owner fences, original-coupon return and invalidation preservation. No new public/internal HTTP mutation endpoint or runtime acceptance claim. Mappings: mall-saas/docs/evidence/yudao-coupon-order-pricing-port.json.


2026-10-29 Member cart: directly copied CartDO, AppCartAddReqVO, AppCartUpdateCountReqVO, AppCartResetReqVO from yudao-module-trade-server, adapting namespace/tenant base/AUTO/count bounds/aggregate expectedVersion. Selected original CartServiceImpl add/count/selection/reset/delete/list composition, CartMapper own queries, AppCartController seven duties, selection/list VO fields, and ProductSkuApiImpl/ProductSpuApiImpl/ProductSkuRespDTO actual SKU/SPU read/projection duties. Original author 芋道源码 and MIT LICENSE retained. Project differences: actual member/storefront, minimal on-shelf sales snapshot, configurable capacity, aggregate version/active SKU uniqueness, merged-total stock validation, same-SPU explicit variant merge, invalid rows retained, real current Vue shopping-bag UI. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-member-cart-port.json. No runtime acceptance claim.


2026-10-29 Checkout settlement: selected TradePriceServiceImpl.calculateOrderPrice, TradePriceCalculateRespBO, AppTradeOrderController.settlement and settlement VOs from yudao-module-trade-server, MemberAddressApiImpl own-address delegation, and CouponApiImpl read delegation. Original author 芋道源码 and MIT LICENSE retained. Project adaptations: actual-member-only fixed read RPCs, existing coupon/delivery calculators, private versioned quote receipt/absolute expiry and minimal public projection. Vue UI uses this project's existing components. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-checkout-settlement-port.json. No order creation or runtime acceptance claim.


2026-10-29 Order inventory: selected ProductSkuUpdateStockReqDTO batch input, ProductSkuServiceImpl.updateSkuStock transactional loop, and ProductSkuMapper.updateStockDecr/updateStockIncr conditional stock arithmetic from yudao-module-mall/product. Original author 芋道源码 and MIT LICENSE retained. Adaptations: parameterized SQL, tenant/order/user binding, expected price/metadata versions, committed order receipt and movement details, cancel-before-deduct fence, fixed lock ordering and whole-unpaid-order recovery. Original early sales_count changes deliberately deferred to confirmed-payment statistics under project rules. Adaptation author: assistant. Hashes: mall-saas/docs/evidence/yudao-order-stock-port.json. No protected HTTP/RPC or runtime acceptance claim.


2026-10-29 Order preparation: selected TradeOrderUpdateServiceImpl.createOrder/buildTradeOrder/buildTradeOrderItems, TradeOrderItemDO cart/product/property/price fields, and AppTradeOrderCreateReqVO remark from yudao-module-trade-server. Original author 芋道源码 and MIT LICENSE retained. Project B06/B10 adaptations: separate non-payable preparation, stable request/quote/member binding, current-authority repricing, immutable line/unit allocations and atomic pending outbox. No generic handler framework or copied excluded promotion branches. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-order-preparation-port.json. No actual order creation, MQ delivery, compensation or runtime acceptance claim.


2026-10-29 Protected creation-resource RPC: selected ProductSkuApi/ProductSkuApiImpl batch stock delegation and CouponApi/CouponApiImpl coupon delegation from yudao-module-mall. Original author 芋道源码 and MIT LICENSE retained. Reused this project's MemberServiceTokenAuthenticationFilter and SystemSecurityFeignConfiguration patterns for a receiver-scoped exact-POST service identity. Project adaptations: frozen preparation-owned command data, dedicated credentials, tenant scope, expected coupon quote before hold, historical result validation, finite/no-retry clients. No public Gateway or compensation endpoints, no actual credential configuration or runtime acceptance. Adaptation author: assistant. Hashes: mall-saas/docs/evidence/yudao-order-resource-rpc-port.json.

2026-10-29 Creation-failure compensation: selected CouponServiceImpl.useCoupon/returnUsedCoupon state/expiry restoration and TradeOrderUpdateServiceImpl.cancelOrder0 / TradeCouponOrderHandler.afterCancelOrder cancellation responsibilities from local yudao-module-mall. Original author 芋道源码 and MIT LICENSE retained. Existing project stock/coupon restore implementations reused; the permanent cancel-before-reserve gate, protected close-creation routes and durable PREPARED/PROCESSING -> COMPENSATING -> FAILED sequence are project-specific consistency adaptations, not original yudao behavior. No generic handler/retry framework added. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-order-creation-compensation-port.json. SQL metadata checked; no runtime compensation/transaction acceptance claim.

2026-10-29 Formal ordinary order creation: copied TradeOrderStatusEnum (original author Sin; packages/common ObjectUtil/ObjectUtils adapted to JDK Objects/Arrays), selected ordinary delivery/amount/order-item fields from TradeOrderDO/TradeOrderItemDO and createOrder/buildTradeOrder/buildTradeOrderItems/afterCreateTradeOrder responsibilities from TradeOrderUpdateServiceImpl. Original author 芋道源码 for those source classes and MIT LICENSE retained. Project adaptations: prepared stable ids, Long cent amounts, snapshot copy, existing protected resource commands, one local main-order/item/log/cart/CREATED transaction, and existing failure compensation. Excluded points/group-buy/pickup branches not imported. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-formal-order-creation-port.json. SQL metadata confirmed; no compilation/runtime creation acceptance claim.

2026-10-29 Payment order core: copied PayOrderStatusEnum and selected PayOrderCreateReqDTO/PayOrderRespDTO, PayAppDO/PayOrderDO fields and PayOrderServiceImpl.createOrder/find responsibilities from local yudao-module-pay; selected trade TradeOrderUpdateServiceImpl.createPayOrder delegation/binding. Original author 芋道源码 and MIT LICENSE retained. Project adaptations: per-tenant apps, fixed trade application, Long cent amounts, immutable user/price/expiry checks, existing service authentication/Feign and local CAS binding/free-price confirmation. Status array() corrected from empty to actual values, ObjectUtils replaced by existing JDK utilities. No original channel SDK/notify/refund implementation claimed migrated yet. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-payment-order-core-port.json. DDL metadata checked; no runtime/payment acceptance claim.

2026-10-29 Alipay QR client: copied PayRefundStatusEnum, PayOrderDisplayModeEnum and four order/refund client DTOs; selected PayClient/PayClientConfig/PayClientFactoryImpl/AbstractPayClient and AbstractAlipayPayClient/AlipayQrPayClient methods from local yudao-module-pay. Original authors 芋道源码 and jason retained, MIT LICENSE applies. Transfers/wallet/WeChat excluded. Adaptations: Long exact cent arithmetic, configured sandbox-only RSA2 app/seller verification, signed-map parsing, no raw response retention, sanitized unknown-result handling, explicit close, fixed SDK timeouts and process call bound. SDK version4.40.1014.ALL matches local upstream; pinned sources inspected but no compilation/runtime payment validation. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-alipay-client-port.json.

2026-10-29 Persistent payment attempt: selected PayChannelDO, PayOrderExtensionDO, PayChannelServiceImpl.getPayClient and PayOrderServiceImpl.submitOrder persistence-before-channel responsibilities from local yudao-module-pay. Original author 芋道源码 and MIT LICENSE retained. Project adaptations: one immutable attempt/no per payment, typed external tenant/channel/version credentials, local closing gate, original-id query recovery and late-payment REVIEW. No generic workflow/retry framework. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-payment-attempt-port.json. SQL metadata confirmed; no actual payment/closure or concurrency acceptance claim.

2026-10-29 Callback/paid synchronization and safe unpaid cancellation: selected PayNotifyController parse->notify->success, PayOrderServiceImpl.notifyOrder/expireOrder, and TradeOrderUpdateServiceImpl.updateOrderPaid/cancelOrderByMember/cancelOrderBySystem/cancelOrder0 responsibilities from local yudao-cloud. Original author 芋道源码 and MIT LICENSE retained. Project adaptations: bounded duplicate-rejecting callback form parser, signed payload binding and mutable SDK verification copy, existing attempt-parent lock, explicit channel close, same-business-key close-before-create record, remote-ledger verified trade paid/cancel CAS, and explicit pending resource-cleanup acknowledgement. Cancel fields use original cancelType/cancelTime semantics (10 timeout/30 member); no new generic handler/retry/audit framework. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-payment-close-cancel-port.json. DDL metadata inspected; compilation/runtime/payment/concurrency not verified.

2026-10-29 Unpaid cancellation resource cleanup: selected TradeProductSkuOrderHandler.afterCancelOrder and TradeCouponOrderHandler.afterCancelOrder API delegation duties from local yudao-module-trade-server, and CouponServiceImpl.returnUsedCoupon restoration responsibilities. Original author 芋道源码 / MIT retained. Directly reused existing project ProductOrderStockService.release and CouponOrderService.returnUsed/gate/restore rather than adding another resource implementation. Project adaptations: exact service-only routes, authoritative cancelled-order snapshot/receipt checks, two-side confirmation, and same-transaction cleanup flag/system-source log. No generic workflow/retry framework, no after-sale/full-refund endpoint or runtime correctness claim. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-cancel-resource-release-port.json.

2026-10-29 XXL/order recovery: selected YudaoXxlJobAutoConfiguration and XxlJobProperties from local yudao-spring-boot-starter-job and TradeOrderAutoCancelJob from trade-server. Original author 芋道源码 and MIT retained. Same upstream xxl-job-core 2.4.0, adapted namespace/dependencies, opt-in default and explicit configuration checks. Project order-specific additions: four fixed existing recovery actions, durable tenant/action/order receipts with lease/bounded backoff, and existing pay attempt active synchronization; no executable payload/generic workflow/retry platform. Original @TenantJob automatic tenant enumeration is not claimed copied: this slice accepts one trusted XXL tenant parameter and clears context. Adaptation author: assistant. Source/target hashes: mall-saas/docs/evidence/yudao-order-recovery-job-port.json. No compilation/executor/job/runtime acceptance claim.

2026-10-29 Merchant recovery administration: selected TradeOrderController permission/page/detail delegation from local yudao-module-trade-server and reused existing local merchant authorization/CAS/page/transaction patterns. Original author 芋道源码 and MIT retained for selected responsibilities. The local upstream tree has no equivalent order-specific manual replay receipt; typed UUID-bound replay/previous-round/result history and the lease-bound transactional result mirror are project additions authored by assistant to preserve original-business identity and operator/result audit. No generic audit/retry framework or independent operations console. Gateway adds only five exact merchant paths; SQL resource seeds do not grant existing packages/roles. Source/target hashes: mall-saas/docs/evidence/yudao-order-recovery-manual-port.json. No compile/runtime/manual replay correctness claim.

2026-10-29 Consumer order HTTP/checkout: selected AppTradeOrderController.createOrder/getOrderPage/getOrderDetail/cancelOrder and TradeOrderQueryServiceImpl own-order/batch-item responsibilities from local yudao-module-trade-server. Original author 芋道源码 and MIT retained. Directly reused existing preparation/create/payment/cancel/resource services, not reimplemented another transaction flow. Project adaptations: stable request/stage result, explicit quote-lock close of unprepared requests, minimal historical member views, GET read-only, POST payment preparation and cancelled-resource receipt semantics. Existing project MemberCheckoutPanel retained; consumer UI is adapted here, not falsely attributed as a copied upstream consumer UI. Source/target hashes: mall-saas/docs/evidence/yudao-member-order-checkout-port.json. No compile/HTTP/order/payment/browser validation claim.


2026-10-29 Consumer own-order UI: wired the existing selected AppTradeOrderController page/get-detail/cancel responsibilities to the project's existing Shop/Modal visual system. Original selected backend authors and MIT remain unchanged. MemberOrdersPanel and TypeScript response/pending-cancel adapters are authored here by assistant, not copied from an upstream consumer UI. Summary/full snapshot validation, original-order cancel acknowledgement recovery and realm/store/member storage isolation are project adaptations. No new backend copy or route/DDL changes in this slice. Source hashes and limitations: mall-saas/docs/evidence/yudao-member-orders-ui-port.json. No typecheck/build/browser/HTTP/runtime acceptance claim.


2026-10-29 Member payment HTTP: selected AppPayOrderController.submitPayOrder/getOrder and PayOrderServiceImpl.submitOrder persistence-before-channel/status/display duties from local yudao-module-pay. Original author 芋道源码/MIT retained. Existing project PayOrderAttemptService/ledger/close/paid synchronization directly reused; no additional channel implementation. Project adaptations: fixed tenant/member/order-derived channel selection, exact service-only submit/snapshot routes, own-order trade HTTP facade and pure GET snapshot vs explicit POST channel query, persisted original-QR projection and local-order confirmation. No source claims for wallet/transfer/multi-channel or unknown precreate QR-loss recovery. Source hashes/limitations: mall-saas/docs/evidence/yudao-member-payment-http-ui-port.json. No build/runtime/sandbox acceptance.


2026-10-29 Original-attempt QR recovery: reused selected original AlipayQrPayClient.doUnifiedOrder, AbstractAlipayPayClient.doGetOrder and existing project attempt/record/payment-confirmation duties. Original authors 芋道源码/jason and MIT retained. recoverQr, exact NOT_EXIST recovery distinction (not close proof), persisted bounded manual recovery and first-stored-QR preservation are project additions authored by assistant, not a claimed copied upstream recovery method. All recovery requests preserve the original attempt number/channel/version/price/deadline and are explicit; no generic retry framework or new channel. V003 additive metadata applied; source/SDK static metadata inspected. Current provider same-number/sandbox/concurrency behavior not verified. Source hashes and official documentation/inference limits: mall-saas/docs/evidence/yudao-original-payment-qr-recovery-port.json.


2026-10-29 Ordinary order fulfillment: selected TradeOrderUpdateServiceImpl.deliveryOrder/receiveOrderByMember/receiveOrder0, AppTradeOrderController.receiveOrder, DeliveryExpressDO (author jason) and TradeOrderDeliveryReqVO from local yudao-module-trade-server. Original authors 芋道源码/jason and MIT retained. Project adaptations: one real express parcel, original item quantity minus completed refunds, parent-locked aftersale pause, immutable original delivery time during tracking correction, current-tenant/member/version boundaries, same-transaction original outbox events, and minimal member HTTP/UI projection. Current Vue UI authored by assistant, not copied consumer UI. Merchant HTTP/UI, actual automatic receipt job and aftersale/refund implementation remain. Adaptation author: assistant. Source hashes and limits: mall-saas/docs/evidence/yudao-order-fulfillment-kernel-port.json. No compile/runtime/business correctness claim.


2026-10-29 Merchant order HTTP/query: selected TradeOrderController.getOrderPage/getOrderDetail/deliveryOrder, TradeOrderQueryServiceImpl original-item lookup/composition, order page VO fields and TradeOrderLogDO semantics from local yudao-module-trade-server. Original authors 芋道源码 and 陈賝 (order log) and MIT retained. Existing fulfillment transactions directly reused. Project differences: current positive merchant tenant, no current member-data enrichment, masked ordinary views versus delivery-purpose recipient snapshot, bounded page/batch/log projection, typed logistics history and existing service-side ss permission checks before local read transaction. No department assignment, generic audit framework, new grant or operations console. Merchant UI remains pending. Adaptation author: assistant. Source hashes/limits: mall-saas/docs/evidence/yudao-merchant-order-http-port.json. No compile/runtime/permission correctness claim.


2026-10-30 Merchant order UI: selected getOrderPage/getOrder/deliveryOrder API duties and OrderDeliveryForm carrier lookup/explicit submit/success-refresh structure from local yudao-ui-admin-vue3-master. Existing MIT attribution retained; this project MerchantOrders.vue and merchant-order.ts authored/adapted by assistant using existing project components, not a claimed wholesale ElementPlus page copy. Differences: current concrete paths, version/current-tenant bearer boundaries, purpose-scoped recipient view, one actual parcel, original logistics correction history, explicit preview confirmation and original-order GET after uncertain writes. No automatic retry or sensitive browser persistence. Source hashes: mall-saas/docs/evidence/yudao-merchant-order-ui-port.json. No build/browser/runtime claim.


2026-10-30 Automatic receipt job: selected TradeOrderAutoReceiveJob.execute -> TradeOrderUpdateServiceImpl.receiveOrderBySystem scan/individual-call responsibilities from local yudao-cloud trade-server. Original author 芋道源码 and MIT retained. Reused existing project tradeOrderRecoveryJob, fixed-action recovery lease/retry/manual replay and receiveBySystem order transaction; no new scheduler/retry framework. Project differences: persisted AUTO_RECEIVE fixed action, current trusted tenant, 14-day original shipment, aftersale pause without consuming failure budget, existing status/log/outbox idempotence. Original @TenantJob automatic enumeration not claimed ported: current configured single-tenant tasks still need actual registration/coverage. Adaptation author: assistant. Hashes/limits: mall-saas/docs/evidence/yudao-auto-receive-job-port.json. No compile/job/runtime verification claim.


2026-10-30 Aftersale unit prerequisites: directly copied AfterSaleStatusEnum (author 芋道源码) and AfterSaleWayEnum (author Sin), adapted namespace and Hutool lookup to JDK. Selected AppAfterSaleCreateReqVO item/way/reason/description and AfterSaleServiceImpl.validateOrderItemApplicable eligibility/original-payment-cap duties from local yudao-cloud. MIT retained. Project additions authored by assistant: quantity/request/version fields, strict seven-day receipt window, pure original-unit quotient/remainder planner and immutable internal result. Occupancy is a trusted input to be loaded under the future parent-lock transaction; no persistence/concurrency/refund success claim. Original shipping share is explicitly not refund authorization; actual freight ledger/quality review remains. Source hashes/limits: mall-saas/docs/evidence/yudao-aftersale-unit-plan-port.json.


2026-10-30 Aftersale application transaction: selected AfterSaleDO application fields, AfterSaleMapper persistence and AfterSaleServiceImpl.createAfterSale validate/save/update applying/log responsibilities from local yudao-cloud trade-server. Original author 芋道源码/MIT retained. Existing project MemberAccess/parent fulfillment lock/unit calculator/outbox reused. Project additions: request-bound receipt, original-unit RESERVED ledger, generated active-item/unit uniqueness, parent pending/version and same-transaction logs/PENDING event. No review/refund or shipping-refund authorization claimed. Quantity/refund state transition and actual application HTTP/UI remain. Adaptation author: assistant. Source hashes: mall-saas/docs/evidence/yudao-aftersale-application-port.json. DDL metadata only, no compile/runtime/concurrency claim.


2026-10-30 Aftersale decisions: selected AfterSaleServiceImpl.agreeAfterSale/disagreeAfterSale/cancelAfterSale state/update/log responsibilities and AfterSaleDO/AfterSaleLogDO review/log fields from local yudao-cloud. Original author 芋道源码/MIT retained. Project changes: original order parent lock, immutable per-command UUID log receipts, version/actor/reason/quality binding, fixed initial return deadline and exact original RESERVED-unit release/pending counter transaction. Unlike original cancellation of BUYER_DELIVERY, submitted returns cannot be erased here; funds execution must later persist a non-cancellable phase/original refund number before external calls. No general audit/retry framework or actual refund. Adaptation author: assistant. Hashes/limits: mall-saas/docs/evidence/yudao-aftersale-decisions-port.json. No compile/runtime/release correctness claim.


2026-10-30 Aftersale return shipping: selected AppAfterSaleDeliveryReqVO, AfterSaleDO logistics fields and AfterSaleServiceImpl.deliveryAfterSale member/carrier/conditional-status/log responsibilities from local yudao-cloud. Original author 芋道源码/MIT retained. Existing project decision transaction/UUID receipt reused. Project additions: explicit per-approved-case merchant return-address snapshot (no existing config module), original seven-day strict deadline, JSON-type-normalized immutable command binding, parent/after-sale/unit lock order and pending reservation preservation. No automatic funds, receipt, in-transit expiry or fake address. Adaptation author: assistant. Hashes/limits: mall-saas/docs/evidence/yudao-aftersale-return-shipping-port.json. No compile/HTTP/runtime acceptance claim.


2026-10-30 Return inspection: selected AfterSaleServiceImpl.receiveAfterSale/refuseAfterSale and AfterSaleDO receiveTime/receiveReason state/update/log responsibilities from local yudao-cloud trade-server. Original author 芋道源码/MIT retained. Existing decision transaction and immutable UUID receipts reused; project additions authored by assistant: cumulative received/salable facts, first inspection timestamp, active dispute35 retaining original units/pending instead of original terminal63 cancellation, and no member cancellation of received returns in40. No funds or stock restoration executed; new permission metadata/HTTP/UI remain. Hashes/limits: mall-saas/docs/evidence/yudao-aftersale-inspection-port.json. No compile or runtime inspection acceptance claim.


2026-10-30 Unsubmitted return expiry/reminder: selected TradeOrderAutoReceiveJob scan -> existing Service responsibilities and AfterSaleServiceImpl.cancelAfterSale state/release/log duties from local yudao-cloud; original author 芋道源码/MIT retained. Existing project decision transaction and order recovery/replay reused. Original local source has no same seven-day return-expiry method; project additions authored by assistant: fixed original-order RETURN_EXPIRE check/rearm completed row, status64 before any parcel, original-unit release/pending/log/outbox local transaction and bounded 48hour log reminders without automatic decisions. No task registration or runtime execution, funds/stock/coupon effect, or general reminder framework. Hashes/limits: mall-saas/docs/evidence/yudao-aftersale-return-expiry-port.json.


2026-10-30 Protected aftersale HTTP/query: selected admin AfterSaleController, AppAfterSaleController and AfterSaleMapper page/owner/detail/log and command-delegation responsibilities from local yudao-cloud trade-server. Original author 芋道源码/MIT retained. Existing project application/decision transactions reused. Query/projection adaptations authored by assistant: current tenant/actual member Host, original-item/order batch ownership checks, bounded RR reads, purpose-scoped return-address lookup, no raw log details, original actor-bound command receipts, exact POST UUID/version commands and separate query/audit/receive resources. No current member phone/name RPC, copied anonymous funds callback, refund execution or claimed whole-module copy. Hashes/limits: mall-saas/docs/evidence/yudao-aftersale-http-port.json. No compile/HTTP/runtime acceptance claim.


2026-10-30 Merchant aftersale UI: selected local yudao-ui-admin-vue3-master afterSale index/detail and AfterSaleDisagreeForm page/detail/log/explicit reason-submit/success-refresh responsibilities, existing MIT retained. Current MerchantAfterSales.vue and merchant-aftersale.ts authored/adapted by assistant to existing project components, not wholesale ElementPlus-page copying. Added current UUID/version/actor-bound unknown-result recovery marker with six non-contact fields only, guarded exact API projection, review/inspection confirmation and prototype separation; no automatic write retries, funds execution or claimed runtime verification. Reused existing AreaUtils/DeliveryAreaOptionRespVO for audit-scoped option GET. Hashes/limits: mall-saas/docs/evidence/yudao-aftersale-merchant-ui-port.json.


2026-10-30 Member aftersale UI: reused existing AppAfterSaleController page/get/create/delivery/cancel and original-actor query duties selected from local yudao-cloud (original author 芋道源码/MIT retained). Local UI reference contains admin aftersale pages, not a claimed ready-made consumer page; MemberAfterSalesPanel.vue/member-aftersale.ts authored by assistant using existing project Shop/MemberOrdersPanel/Modal and actual member HTTP. Current order-item/count/version commands omit client refund amounts; existing query guards/types extracted unchanged to pure aftersale-contract.ts and merchant public exports retained. Additions: purpose-only return address/regions/carriers, original-scoped minimal request markers, no automatic write retries, known-error-only marker clearing; no backend/SQL/funds implementation or runtime claim this slice. Source hashes/limits: mall-saas/docs/evidence/yudao-aftersale-member-ui-port.json.

2026-10-30 Original refund preparation: selected AfterSaleServiceImpl.refundAfterSale/createPayRefund eligibility, stable original refund identity and result-separation responsibilities, and PayRefundDO ownership/amount/status fields from local yudao-cloud (author 芋道源码/MIT retained). Current trade-side prepare service/ledger and source adaptations authored by assistant: immutable actor-bound UUID receipt, non-cancellable45, original-unit and original-freight balances, one unresolved financial plan per order, and frozen stock/coupon recovery basis. Existing decision/calculator/parent-lock/log/outbox duties reused; no general funds engine, channel calls, confirmed refund, zero-price immediate completion, stock/coupon effect, new HTTP or runtime acceptance. Source hashes/limits: mall-saas/docs/evidence/yudao-aftersale-refund-preparation-port.json.

2026-10-30 Payment refund core: selected local PayRefundDO and PayRefundServiceImpl.createRefund/validatePayOrderCanRefund/notifyRefundSuccess/syncRefund stable business identity, channel amount/result and parent refund duties (author 芋道源码/MIT retained). DTOs/Mapper/concrete service adapted by assistant to existing explicit SQL/TransactionTemplate, fixed original trade refund number, confirmed/reserved parent balance and non-success unique slot; channel calls occur outside local transactions, private verified success atomically updates payment/refund records. Reused existing Alipay client with official fund_change=Y confirmation and optional unused callback URL; no additional channel, raw SDK persistence, public/internal refund HTTP, stock/coupon/trade-success effect or runtime claim. Existing normal paid-close race and already-confirmed payment/refund read compatibility adjusted. Hashes/limits: mall-saas/docs/evidence/yudao-payment-refund-core-port.json.

2026-10-30 Refund RPC/trade confirmation: selected local PayRefundApi/Impl delegation, AfterSaleServiceImpl.createPayRefund/updateAfterSaleRefunded and TradeOrderUpdateServiceImpl.updateOrderItemWhenAfterSaleSuccess/cancelOrderByAfterSale original result/accounting duties (author 芋道源码/MIT retained). Existing fixed service authentication/OrderPayFeignApi/OrderResourceClient and original parent/unit/log/outbox reused. Adaptations authored by assistant: exact five refund service POSTs, original-plan-only request construction/response validation, payment-version protected progress, private verified local confirmation including zero-amount physical obligations, original-unit counted close preserving shipment history, and separate stock/coupon pending flags/query projections. Existing calculator reused for common original balance verification, not a new workflow framework. Current consumer/admin UI changes authored by assistant; no public funds action, actual stock/coupon RPC or runtime claim. Hashes/limits: mall-saas/docs/evidence/yudao-refund-rpc-confirmation-port.json.

2026-10-30 Refund resource recovery: selected TradeProductSkuOrderHandler.afterCancelOrderItem incremental original-item restoration and TradeCouponOrderHandler.afterCancelOrder/CouponServiceImpl.returnUsedCoupon duties from local yudao-cloud (author 芋道源码/MIT retained). Reused existing ProductOrderStockMapper stock locks/increment/summary and CouponOrderService FULL_REFUND domain unchanged apart from documentation. Adaptations authored by assistant: original aftersale stock receipt/cumulative original deduction bound, no whole-unpaid-release overlap, fixed service routes and plan-only requests, original coupon expiry/risk preservation, independent RPC receipt checks and trade completion logs/outbox, retry of incomplete resources after confirmed funds without reissuing money. Zero/salable-only original obligations retained. No generic recovery framework, additional channel, public financial HTTP or runtime acceptance claim. Hashes/limits: mall-saas/docs/evidence/yudao-refund-resource-recovery-port.json.
# 2026-10-30：原退款财务HTTP

择取上游 `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/aftersale/AfterSaleController.java` 的 `refundAfterSale` 权限与Service委托职责，保留原作者及本目录MIT许可。当前后台Controller继续使用原 `trade:after-sale:refund` 权限；助手按已确认原UUID/原资金号及未知结果规则，接入既有准备/执行/同步，并新增不触发业务写入的本地读取、显式返回投影与商家适配服务。未复制上游匿名成功回调入口；当前业务来源和差异见 `mall-saas/docs/design/REFUND-FINANCIAL-HTTP.md` 与对应摘要证据。未运行资金/资源业务验证，不将本片段记作完整退款交付。



## 2026-10-30：商家售后财务操作页面

择取本地yudao-ui-admin-vue3-master/src/views/mall/trade/afterSale/detail/index.vue的refund方法与src/api/mall/trade/afterSale/index.ts接口职责，保留上游确认、调用、刷新流程及本目录许可声明。助手适配到已有青禾售后页和HTTP组件，增加原准备/执行/同步确认、资金与资源分开显示，以及当前actor的原UUID/RF查询标记；仅保存原引用，不保存原因、金额或凭据。新增merchant-refund.ts复用原商家请求与纯退款进度校验，不引入原版ElementPlus。原后台财务GET增加受权限保护的按售后id找回RF能力，POST仍核对原号。来源摘要、实际差异和未运行边界见mall-saas/docs/design/REFUND-FINANCIAL-UI.md和对应port证据。


## 2026-10-30：原退款确认与恢复任务

择取上游PayRefundSyncJob.execute的XXL委托退款同步职责，接入已有TradeOrderRecoveryJob/Service租约、退避和重放实现，不新建通用框架。助手适配首次财务执行确认与双日志/Outbox、每售后固定REFUND目标、缺确认不执行（含零金额）、原首次SUBMIT或查询/资源恢复、退款任务重排额外资金权限。原PayRefundService预算及原号保持。V024修复前版日志CHECK遗漏的准备/成功分支并添加确认/目标约束，未修改业务数据。原作者/MIT保留，摘要与运行边界见mall-saas/docs/design/REFUND-EXECUTION-RECOVERY.md和对应port证据。


## 2026-10-30：受审计原号重试

择取AfterSaleServiceImpl.refundAfterSale状态核对、退款委托和日志职责，复用已移植支付先查原号及有界重试。助手适配UUID/版本/原因双日志审计与事务外RPC；同UUID不重复支付，不声称上游已有同名接口。原作者/MIT保留，来源与限制见mall-saas/docs/design/AUDITED-REFUND-RETRY.md及对应port证据。


## 2026-10-30：原退款人工一次机会授权

复用此前PayRefundServiceImpl原号账本/查询/提交职责。助手为已确认的预算耗尽恢复需求增加pay侧UUID/actor/reason不可变回执与原父锁事务预算+1，原次数/金额/号不重置；默认3不变，不称上游已有此协议。保留原作者/MIT。当前只内部授权，trade财务/页面未接；来源摘要与运行限制见mall-saas/docs/design/REFUND-CONTINUATION-AUTHORIZATION.md及对应port证据。


## 2026-10-30：原退款人工授权交易接线

沿用上游后台refundAfterSale资金权限/Service委托与已移植原退款/RPC职责。助手增加当前财务actor的原UUID/双版本/原因审计，事务后原号一次机会授权、只读预算投影，复用pay端幂等；不自动提交退款。作者/MIT保留，非声称原版已有人工协议。来源/边界见mall-saas/docs/design/REFUND-CONTINUATION-TRADE.md及对应port证据。


## 2026-10-30：原人工授权UUID查询

沿用退款账本查询/Service委托及既有RPC，助手增加原actor UUID的交易申请与pay授权回执分离读取，缺回执不自动授权，不用当前预算推断原UUID成功。原作者/MIT保留，不声称原版已有双阶段协议。来源及未运行边界见mall-saas/docs/design/REFUND-CONTINUATION-RESULT.md及对应port证据。


## 2026-10-30：交易Outbox发布

择取RocketMQWebSocketMessageSender的Template构造注入/syncSend和原XXL委托职责，保留作者/MIT；同版本2.3.6库作为依赖，不复制第三方SDK代码。助手适配原交易Outbox的短租约/结果落账/原eventId至少一次投递、白名单消息、明确opt-in，未增加通用事件框架。来源及未运行边界见mall-saas/docs/design/TRADE-OUTBOX-PUBLISHER.md和对应port证据。


## 2026-10-30：交易事件消费

择取原RocketMQWebSocketMessageConsumer.onMessage业务委托与TenantRocketMQConsumeMessageHook单条租户传播/清理，保留作者与MIT。助手适配CLUSTERING显式客户端、共享信封与本地原Outbox核对、同库消费回执/原恢复意图事务，不声称上游已有此交易协议，不新增通用框架。来源摘要与未运行边界见mall-saas/docs/design/TRADE-EVENT-CONSUMER.md及对应port证据。


## 2026-10-30：原交易事件查询/重放

择取PayNotifyController通知任务分页/详情权限及Service委托，沿用现有订单恢复管理实现。助手增加固定Outbox原号重放代次及不可变请求记录，不删除消费回执、不改业务任务或资金授权。保留作者/MIT，不称原版已有该协议。来源/差异/未运行边界见mall-saas/docs/design/TRADE-EVENT-ADMIN.md及对应port证据。


## 2026-10-30：原订单到期提醒

沿用TradeOrderAutoCancelJob与TradeOrderUpdateServiceImpl的过期发现/逐单取消及已付保护职责，原作者/MIT保留。助手增加原建单事务固定到期Outbox、原期限native SDK延迟发送、同恢复行提交后触发与提前到达保护，不称原版trade已有同名MQ实现；不复制SDK源码或新增通用框架。来源/差异/未运行边界见mall-saas/docs/design/ORDER-EXPIRY-EVENT.md和对应port证据。


## 2026-10-30：交易任务租户遍历

择取TenantJobAspect的逐租户委托、TenantUtils上下文和异常隔离/结果职责，保留作者/MIT。助手适配为仅两个交易任务的本地原义务租户发现、有限批次/冻结上界/分片游标和租约；不经system启停筛选、不重写既有starter或新增通用调度框架。来源/差异/未运行边界见mall-saas/docs/design/TRADE-TENANT-JOB.md及对应port证据。


## 2026-10-30：DEC-067构建修复

助手按用户授权全后端跳过测试打包，补system-api Feign核心依赖并调整商品VO嵌套类型的Java8注解写法，保留校验与现有上游来源/作者/MIT声明。33/33模块package成功不等于业务验收；实际修改/构建日志/产物摘要见mall-saas/docs/design/FULL-BACKEND-PACKAGE.md及evidence/full-backend-package-20261030.json。


## 2026-10-30：应用文件日志与轮转

择取本地trade/gateway的logback-spring.xml中Console/RollingFile/SizeAndTimeBasedRollingPolicy及AsyncAppender，保留作者/MIT。助手适配公共include、按应用命名、gzip/归档总量上限和已有MDC字段，不引入SkyWalking/APM或额外日志后端。来源/差异/未运行边界见mall-saas/docs/design/APPLICATION-LOG-ROTATION.md与对应port证据。


## 2026-10-30：请求日志编号

择取原monitor TraceFilter响应trace-id/继续链职责，保留作者/MIT。助手为未引入完整APM的项目增加UUID相关号、Servlet请求MDC清理、Gateway Reactor Context与已有Feign/WebClient显式传递，不称原版已有本项目无代理实现。来源/边界见mall-saas/docs/design/REQUEST-TRACE.md及对应port证据。


## 2026-10-30：可信日志身份

沿用此前TenantSecurityWebFilter的已认证租户核对职责与原作者/MIT。助手在两个现有桥接及TraceFilter中补MDC/服务器结束日志属性生命周期，不改认证授权或新增通用日志框架。来源/静态边界见mall-saas/docs/design/TRUSTED-IDENTITY-LOG.md和对应port证据。


## 2026-10-30：本批启动修复

DEC-068授权启动检查发现网关Logger依赖与单机Redisson空密码问题，助手按现有网关Logger方式及RedissonAutoConfigurationCustomizer扩展点修复，不改原版许可/作者或声称此版本兼容修复来自yudao。构建/失败/产物与健康边界见mall-saas/docs/design/LOCAL-STARTUP-CHECK.md。


## 2026-10-30：基础成交退款汇总

择取statistics TradeOrderStatisticsServiceImpl.getOrderSummary与TradeOrderStatisticsMapper COUNT/SUM，保留owen作者/MIT。助手适配到trade本服务账本，按确认的pay_time/refund_time区间和累计支付订单净额分离、整数分字符串、双既有权限及有限日期范围；不新建统计微服务/报表框架。来源及未运行边界见mall-saas/docs/design/TRADE-OPERATING-SUMMARY.md及对应port证据。


## 2026-10-30：秒杀商品库存划拨前置

择取原ProductSkuMapper.updateStockDecr/updateStockIncr条件增减及SeckillActivityServiceImpl库存职责，保留芋道源码/halfninety作者与MIT。助手复用本项目已有SPU/SKU锁、归还与汇总，适配发布原号凭证、实际划拨量、同事务流水和关闭先到保护；不称原版已有本项目跨服务发布恢复协议。无HTTP/Redis/MQ入口，来源、迁移和未运行边界见mall-saas/docs/design/SECKILL-PRODUCT-ALLOCATION.md及对应port证据。


## 2026-10-30：秒杀活动草稿

择取SeckillActivityServiceImpl的create/update/get及validateProductExists职责、SeckillActivityDO/SeckillProductDO结构，保留halfninety/HUIHUI作者与MIT。助手适配当前原Bearer/数据范围商品快照、UTC、草稿版本与零义务保护及配置审计，不声称原版已有本项目独立池发布恢复协议。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-ACTIVITY-DRAFT.md及对应port证据。


## 2026-10-30：秒杀活动管理入口

直接择取原SeckillActivityController的create/update/get/page及Service分页后批量拼接职责，保留halfninety/MIT。助手适配现有草稿、商家realm、版本/零义务限制、本页池汇总及精确Gateway/隐藏权限资源；不开放原版直接启用/关闭/删除。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-ACTIVITY-ADMIN.md及对应port证据。


## 2026-10-30：秒杀发布准备

沿用SeckillActivityServiceImpl.validateProductExists/validateProductConflict职责与halfninety/MIT。助手适配本项目SKU持久互斥、半开时间冲突、原操作者/UUID/版本准备凭证和冻结库存元组，复用现有商品快照及事务/审计；仅状态10，不称原版已有本项目专属池跨服务恢复协议。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-PUBLISH-PREPARATION.md及对应port证据。


## 2026-10-30：秒杀库存内部调用

沿用原ProductSkuApiImpl委托与LeeYan9作者/MIT，复用本项目TradeCommandAuthenticationFilter及OrderResourceFeignConfiguration。助手为固定promotion库存命令适配独立空值关闭凭据、原号计划读取/结果核对，不称原版已有本项目专属库存发布恢复。来源/边界见mall-saas/docs/design/SECKILL-STOCK-RPC.md及对应port证据。


## 2026-10-30：秒杀库存结果记账

沿用SeckillActivityServiceImpl.updateSeckillStockDecr/updateSeckillStockIncr条件池更新和事务职责，保留halfninety/MIT。助手接原发布计划/RPC，增加已确认版本回执、池与额度流水同事务、未发布准备失败及全部原SKU关闭补偿，不称原版已有本项目异步专属库存协议。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-STOCK-RESULT.md及对应port证据。


## 2026-10-30：秒杀发布与失败结清裁定

沿用SeckillActivityServiceImpl活动创建/关闭的状态更新职责，保留halfninety/MIT。助手按已有原号计划/库存回执/池/guard实现全部规格确认后的发布、未发布失败全部关闭后的结清与同事务审计，不称原版已有本项目专属池恢复协议；无Redis/消费者入口。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-PUBLISH-DECISION.md及对应port证据。


## 2026-10-30：开卖前撤回与发布历史

沿用SeckillActivityServiceImpl.closeSeckillActivity状态写职责与halfninety/MIT，助手复用当前父锁/guard/回执/补偿与历史分页，限制开始前epoch0无占额才撤回并留WITHDRAW证明，历史按原UUID审计读不推进库存。不称原版已有本项目跨服务专属池协议。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-WITHDRAW-HISTORY.md及对应port证据。


## 2026-10-30：结清重编与发布新代次

沿用SeckillActivityServiceImpl.updateSeckillActivity主从配置更新职责与halfninety/MIT，助手复用现有结清/父锁/guard/原号审计，补完整配置不可变历史、严格当前与只读历史分界、REOPEN原号回执、新发布代次及额度流水代次，不称原版已有本项目跨服务恢复协议。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-REPUBLICATION.md及对应port证据。


## 2026-10-30：商家发布恢复HTTP

择取原SeckillActivityController映射/校验/权限/委托与halfninety/MIT，助手接既有本项目原请求生命周期Service及不泄露内部库存号的纯读结果投影，精确路由/隐藏publish资源，不称原版有本项目跨服务恢复协议。来源/迁移/未运行边界见mall-saas/docs/design/SECKILL-PUBLISH-HTTP.md及对应port证据。


## 2026-10-31：不可变发布定义Redis预载

择取system OAuth2AccessTokenRedisDAO的StringRedisTemplate、JsonUtils、Key和期限访问职责，保留芋道源码作者与MIT。只读检查原SeckillActivityServiceImpl库存职责；该版本无本项目epoch/跨服务准入恢复，未声称直接复制同一秒杀协议。助手新增不可变定义、原快照/全回执校验、单Key Lua比较安装/匹配及事务外Redis/二次DB核对，不含可变库存或ready/epoch。来源摘要与边界见mall-saas/docs/design/SECKILL-MANIFEST-PRELOAD.md、evidence/yudao-seckill-manifest-preload-port.json。


## 2026-10-31：首次epoch数据库栅栏

保留原SeckillActivityServiceImpl条件更新/本地事务职责与halfninety/MIT，复用当前原发布/父锁/全回执逻辑。首次epoch持久凭证、定义摘要、活动CAS与EPOCH_INIT审计为本项目适配，不称原版存在此恢复协议；不初始化Redis或开启接单。见mall-saas/docs/design/SECKILL-INITIAL-EPOCH.md及evidence/yudao-seckill-initial-epoch-port.json。


## 2026-10-31：暂停epoch seed物化

沿用已择取的OAuth2AccessTokenRedisDAO模板/JSON/期限职责及芋道源码MIT，复用现有单写Lua和活动父锁，不新增通用框架。助手新增独立暂停seed、状态0安装/10只读比对、DB确认/CAS/审计；上游无该跨服务epoch恢复协议。seed不是运行库存，无READY/开放入口。来源与未运行边界见mall-saas/docs/design/SECKILL-PAUSED-SEED.md和evidence/yudao-seckill-paused-seed-port.json。


## 2026-10-31：首次运行库存初始化窗口

复用既有原RedisDAO/StringRedisTemplate/JsonUtils访问职责、芋道源码MIT和本项目活动事务。固定30秒原token/DB期限许可、独立PAUSED运行hash和Lua有界初始化为本项目实现；不称原版已有或窗口单独已完成接单恢复。来源及未运行边界见mall-saas/docs/design/SECKILL-INITIAL-RUNTIME.md、evidence/yudao-seckill-initial-runtime-port.json。


## 2026-10-31：运行epoch就绪与停止

沿用SeckillActivityServiceImpl.closeSeckillActivity状态校验/更新职责与halfninety/MIT，复用现有父锁/版本/审计及RedisDAO。固定窗口OPEN、DB READY、先DB停止再Redis关闭与缺Key墓碑为本项目适配；不称原版直接已有，不宣称完整申请/清算或并发验收。见mall-saas/docs/design/SECKILL-RUNTIME-LIFECYCLE.md及evidence/yudao-seckill-runtime-lifecycle-port.json。


## 2026-10-31：秒杀专用报价

择取TradeSeckillActivityPriceCalculator.calculate/validateJoinSeckill单商品、活动价/限额校验和SeckillActivityServiceImpl.validateJoinSeckill职责，保留HUIHUI/halfninety及MIT声明，复用已移植TradeDeliveryPriceCalculator（jason）运费算法。tenant/generation/READY查询、原请求回执、AES-GCM私密快照与单独trade表为本项目业务适配，非原版已有的库存预占/异步建单协议。见mall-saas/docs/design/SECKILL-ENCRYPTED-QUOTE.md和对应port证据；未运行或宣称完整秒杀验收。


## 2026-10-31：原秒杀报价绑定与关闭栅栏

沿用TradeOrderUpdateServiceImpl.cancelOrder0状态条件更新和内部API委托职责，保留芋道源码/MIT；直接复用本项目已移植的父锁/报价绑定、TradeCommandAuthenticationFilter和Feign访问边界。原报价claim/result/close、关闭先到永久栅栏及quote UUID稳定申请引用为本项目跨服务增量，上游没有该同名协议，不冒称直接整段复制或实际事务验收。见mall-saas/docs/design/SECKILL-QUOTE-BINDING.md及对应port证据。


## 2026-10-31：秒杀原意图、Redis预占和持久受理

沿用SeckillActivityServiceImpl.validateJoinSeckill/updateSeckillStockDecr状态/时间/限购/事务内条件扣减职责，保留halfninety/MIT；直接复用已有本人商品API、交易报价绑定、父锁/CAS/流水/Outbox、RedisDAO和本人投影。原epoch R/C/X单hash脚本、持久拒绝栅栏及异步受理协议为本项目业务增量，本地上游没有同名协议，不冒称整段复制或实际原子性验收。见mall-saas/docs/design/SECKILL-ATOMIC-ADMISSION.md与对应port证据。


## 2026-10-31：秒杀租约发布与权威读取桥接

直接择取本项目已有TradeOrderEventPublisher/OutboxMapper/EventEnvelope/EventConfiguration/OrderResourceFeignConfiguration，保留原作者声明和MIT来源链。原syncSend来自yudao RocketMQWebSocketMessageSender.sendRocketMQMessage（芋道源码）；租约Outbox、规范秒杀协议、受理事实证明及跨服务读回为项目业务适配，上游无本项目同名完整异步协议。未注册空消费者或宣称真实Broker/订单验收，见mall-saas/docs/design/SECKILL-EVENT-DISPATCH.md和对应port证据。


## 2026-10-31 秒杀订单保存与来源区分

择取本地yudao-module-trade-server的TradeOrderUpdateServiceImpl.createOrder/buildTradeOrder/buildTradeOrderItems（芋道源码，MIT），复用已移植OrderMapper主从/日志/到期Outbox及DO字段；type=1沿原TradeOrderTypeEnum.SECKILL。新增TradeSeckillOrderCreationService用于独立promotion异步受理后的本地事务保存，与上游同步handler链不同；共享准备自增ID、source_type/无购物车项、原报价密文/摘要复核、原绑定/Inbox/结果Outbox及服务间来源证明为本项目业务差异，不冒称上游已有完整异步协议。原许可和作者声明继续保留，未复制私有业务代码。


## 2026-10-31 秒杀建单结果回传

直接择取现有SeckillEventPublisher/OutboxPublishMapper/EventConfiguration发送租约职责，仅替换trade结果域/表/Topic/Bean，原发送来源yudao RocketMQWebSocketMessageSender.sendRocketMQMessage（芋道源码/MIT）继续保留。promotion池与个人held的条件增减职责对照yudao-module-mall/yudao-module-promotion-server的SeckillActivityServiceImpl.updateSeckillStockIncr/Decr（halfninety/HUIHUI）；独立划拨池、原事件来源证明、建单绑定/关闭串行化、最终回执和Redis待同步记录为本项目跨服务业务适配，上游没有本项目同名完整异步协议。没有复制公司私有业务或另造通用重试框架。


## 2026-10-31 已受理关闭Redis同步

复用本项目既有SeckillAdmissionRedisDAO、18参数单hash预占Lua、V019最终结果回执及后台按原号恢复职责，只扩CLOSE_ACCEPTED与PENDING确认，不另建通用重试框架。原库存恢复职责继续对照yudao SeckillActivityServiceImpl.updateSeckillStockIncr（halfninety/HUIHUI、MIT）；本地上游无本项目R/C/X及永久关闭/本库退占用证明协议，本段为既有异步业务差异，未冒称直接复制上游完整关闭缓存实现。原作者和许可继续保留。


## 2026-10-31 秒杀命令与结果消费者

直接择取本项目已有TradeOrderEventConsumer/TradeEventConfiguration单条校验、租户上下文/清理、业务委托/回执后ACK及显式DefaultMQPushConsumer参数，不新建通用监听抽象。原职责来源仍为yudao RocketMQWebSocketMessageConsumer.onMessage委托（芋道源码/MIT）；原版WebSocket广播与本项目订单CLUSTERING不同，原规范事件/来源证明/最终事务回执协议为本项目异步业务差异。保留原作者及许可，不冒称上游已有本项目完整异步秒杀消费者。


## 2026-10-31 秒杀固定后台任务

直接复用现有TradeOrderEventPublishJob/TradeTenantJobService/游标Mapper的XXL委托、分片/租约/本地义务轮转/清理职责，将结果发送作为原trade固定任务分支，promotion沿同样结构适配命令发送和关闭缓存同步。上游来源为TradeOrderAutoCancelJob.execute及TenantJobAspect（芋道源码/MIT）；本服务义务租户发现、固定持久游标、单租户缓存keyset CAS是本项目业务适配，不新建通用调度或重试框架。原许可/作者保留，未调用XXL Admin或初始化运行任务。


## 2026-10-31 秒杀建单超时协调

沿已有TradeOrderAutoCancelJob.execute/租户任务协调职责（芋道源码/MIT），直接复用现有SECKILL_CLOSE Outbox/命令消费者/永久binding/结果消费者及原申请retry字段；未增加另一个跨服务关闭/释放RPC或通用重试框架。promotion只持久化30与关闭事件、trade以本地原绑定裁决再返回结果为本项目异步受理业务差异，原作者/许可继续保留。

## 2026-10-31 原受理意图恢复

秒杀显式付款/QR后续适配（2026-10-31）：直接复用已有TradeOrderPaymentService/OrderResourceClient/原支付QR恢复，原职责来自TradeOrderUpdateServiceImpl.createPayOrder/updateOrderPaid（芋道源码/MIT）；type1真实冻结申请资格只读核对是项目跨服务差异，未另建支付渠道、二维码流程或通用框架，原作者/许可保留。

原订单终局自动核对后续适配（2026-10-31）：SeckillOrderReconcileJob/Service沿TradeOrderAutoCancelJob.execute/TenantJobAspect（芋道源码/MIT）逐笔委托职责，复用既有固定租户协调器、资源转换/缓存/握手逻辑，新增原申请到期检查元数据/CAS为本项目跨服务恢复差异，不构建通用流程或重试框架。原作者/许可保留。

秒杀取消资源完成握手后续适配（2026-10-31）：直接复用原取消资源Service/原完成SQL和日志、既有读客户端/服务认证及原CANCEL_RELEASE租约预算，职责来源TradeProductSkuOrderHandler/TradeCouponOrderHandler.afterCancelOrder（芋道源码/MIT）。原终局/流水/缓存ACK的跨服务核对为本项目协议适配，不另造通用证明或重试框架，原作者/许可保留。

秒杀生命周期恢复后续适配（2026-10-31）：直接复用TradeOrderRecoveryService/Mapper/消费者已有固定动作、租约预算与审计，沿TradeOrderAutoCancelJob.execute/cancelOrderBySystem（芋道源码/MIT）委托职责补type1分流；专用付款查询仍复用原客户端，未新增通用重试/任务框架。原作者/许可保留。

秒杀已建订单关单后续适配（2026-10-31）：TradeSeckillOrderCloseService及支付后台入口直接复用现有普通取消/付款确认/支付客户端/原条件SQL，原职责来源TradeOrderUpdateServiceImpl.cancelOrderBySystem/updateOrderPaid（芋道源码，MIT）。原绑定/准备/订单校验静态复用、后台专用边界和先永久关闭支付为本项目跨服务业务适配，不另造支付渠道、通用流程或重试框架。原作者/许可保留。

订单缓存持久调度后续适配（2026-10-31）：SeckillOrderCacheSyncJob/固定协调器继续直接复用既有关闭缓存Job及本地yudao TradeOrderAutoCancelJob.execute/TenantJobAspect（芋道源码/MIT）委托/租户清理职责；原缓存表双位置/共享CAS和本地PENDING发现为本项目业务适配，不新增通用调度框架或改原作者归属。原许可声明保留。

订单终局Redis投影后续适配（2026-10-31）：直接复用既有关闭缓存同步/18参数单hash Lua、事务资源流水和原意图恢复，补付款P/订单取消X及提交后ACK。原库存职责来源为SeckillActivityServiceImpl.updateSeckillStockIncr（HUIHUI/芋道源码，MIT）；本项目冻结申请与缓存终局协议为已有跨服务需求适配，非上游原有完整机制，不新建通用恢复框架。原作者/许可保留。

资源转换后续适配（2026-10-31）：沿本地SeckillActivityServiceImpl.updateSeckillStockIncr（HUIHUI/芋道源码，MIT）与本项目已有活动父锁/条件库存/会员额度/流水职责，补原订单付款及未付取消的一次终局；直接复用原真实订单读取、既有表与事务模板。跨服务原资源终局/累计限购为明确业务差异，不新建流程或重试框架。原作者/许可声明保留。

订单状态读取后续适配（2026-10-31）：TradeSeckillResultReadService直接复用现有结果事件的真实订单/准备/商品校验，原职责来源仍为TradeOrderMapper.selectByIdAndUserId及查询Service（芋道源码/MIT）；新增原冻结申请到跨服务财务状态白名单为本项目秒杀业务差异。复用既有Feign/服务认证/只读事务，没有新增通用证明框架，原作者/许可声明保留。

本节后续适配（2026-10-31）：TradeSeckillBindingCleanupJob/Service沿用TradeOrderAutoCancelJob.execute/TenantJobAspect职责（芋道源码/MIT），直接复用原会员父锁、永久关闭绑定/清密文事务、固定租户任务和迟到消息处理。原报价期限覆盖全部合法付款期限后才关闭state0绑定，为本项目跨服务冻结报价业务差异；不宣称原版已有同一协议，不另建通用清理框架，原作者与许可保留。

固定任务委托继续沿用本地TradeOrderAutoCancelJob.execute/TenantJobAspect（芋道源码/MIT）和既有租户任务适配。原trade关闭接口、冻结意图、Redis Lua及关闭结果回执直接复用；本次只补原意图到期裁决/后台确认与缓存进度，不另建恢复框架。promotion/trade跨服务意图及永久关闭证明为本项目业务差异，不冒称原版已有同一协议。原作者与许可声明保留。


成功退款库存义务后续适配（2026-10-31）：职责来源为TradeProductSkuOrderHandler.afterCancelOrderItem（芋道源码，MIT）的售后按原订单项恢复库存。直接复用本项目已有原售后单位/分摊、精确服务认证、Feign结果客户端和PAID资源流水，专属池需按原代次登记待恢复义务，不能套用普通SKU库存回补；售后唯一号、原申请锁内累计约束为本项目已确认专属库存/部分退款差异，不冒称原版已有同一跨服务协议，不新建通用退款/重试框架。原作者和许可保留。

退款专属池/缓存后续适配（2026-10-31）：职责来源为SeckillActivityServiceImpl.updateSeckillStockIncr（HUIHUI）和TradeProductSkuOrderHandler.afterCancelOrderItem（芋道源码），MIT许可保留。直接复用本项目原条件库存/活动父锁、原退款/付款流水、付款Redis同步和既有Lua；原代次恢复与累计退款差额/提交后ACK为已确认专属库存、部分售后与跨服务失败差异，不冒称原版已有此完整协议，不另建通用恢复框架。

退款完成握手后续适配（2026-10-31）：沿用TradeProductSkuOrderHandler.afterCancelOrderItem与TradeOrderUpdateServiceImpl.updateOrderItemWhenAfterSaleSuccess（芋道源码/MIT）的售后恢复和事务职责，直接复用现有mark事务、普通库存结果、Feign精确服务认证及原REFUND恢复预算/租约/人工处理。秒杀专属池/缓存证明和精确服务恢复命令为已确认跨服务库存差异，不伪造普通库存、不另建任务/重试框架。原作者与许可保留。

商品侧秒杀累计归还后续适配（2026-10-31）：职责来源为ProductSkuMapper.updateStockIncr（芋道源码/MIT）；直接复用现有ProductSeckillAllocationService、ProductOrderStockMapper.restore、SPU/SKU锁、汇总和专用服务认证。原划拨不增销量，归还不套用原销量回减；累计目标差额/原凭证与唯一RETURN流水为已确认专属池/迟到请求差异。REVIEW40语义保留，部分归还用独立50；无通用库存/重试框架。原作者及许可保留。

闭场库存预留后续适配（2026-10-31）：沿用SeckillActivityServiceImpl.updateSeckillStockIncr（HUIHUI/芋道源码，MIT）的活动库存职责；直接复用既有原冻结发布/实际回执、原epoch停止/Redis关闭及活动/SKU锁。原累计目标和同事务returnPending预留为已确认专属池跨服务归还与迟到响应差异，无通用清算/重试框架。原作者及许可保留。

2026-10-31：闭场归还确认沿用SeckillActivityServiceImpl.updateSeckillStockIncr库存更新职责；跨product/promotion专属池增加固定原号/累计目标、事务外查结果和本地回执/流水确认。来源作者HUIHUI及MIT声明保留；非整类照搬。

2026-10-31：闭场归还缓存投影继续复用本项目原runtime control/订单缓存同步，原库存职责参考yudao SeckillActivityServiceImpl.updateSeckillStockIncr；新增CLOSED原累计目标差额Lua与提交后独立ACK/有界恢复，非上游已有跨服务代码整段复制，原作者/MIT归属保留。

2026-10-31：闭场原归还恢复Job直接择取已有SeckillOrderCacheSyncJob/固定租户游标代码，原来源TenantJobAspect逐租户执行/异常隔离；新增固定POOL_RETURN_RECOVERY、原本库待完成义务发现与独立afterReturn位置，保留芋道源码作者/MIT。未复制上游通用任务框架或重写既有调度层。

2026-10-31：停止原活动池自动归还准备复用已有SeckillPoolReturnService.prepare与固定租户任务，原库存职责参考SeckillActivityServiceImpl.updateSeckillStockIncr/作者HUIHUI，任务委托参考TenantJobAspect/芋道源码；新增共享候选SQL和独立池id游标，保留MIT归属，不称上游已有跨服务累计归还流程。

2026-10-31：运行活动到期直接复用已有stopInitialEpoch/停止SQL/runtime.close，原关闭职责参考SeckillActivityServiceImpl.closeSeckillActivity/halfninety；固定Job沿既有任务与TenantJobAspect/芋道源码，新增系统DB UTC到期、EPOCH_EXPIRE审计和停止缓存ACK，不称上游已有跨服务清算，MIT归属保留。

2026-10-31：运行许可前到期关闭复用已有原发布定义构造/系统到期任务/累计商品归还，原关闭职责来自SeckillActivityServiceImpl.closeSeckillActivity；新增明确未运行关闭40与持久证明，不伪造运行许可，原作者/MIT归属保留，不称整段上游已有协议。

2026-10-31：原代次清算基线沿用已有停止/商品回执/额度流水职责，原关闭职责参考SeckillActivityServiceImpl.closeSeckillActivity。不可变多代次余额基线及实际终局流水门槛为本项目售后隔离增量，未称上游已有整套实现，原作者/MIT归属保留。

2026-10-31：历史售后归还计划复用已有固定累计归还/库存回执/退款职责；原代次清算基线、稳定售后关联和来源隔离为本项目增量，yudao无相同跨代次协议，不虚构整段上游对应方法，原库存作者/MIT归属保留。

2026-10-31：历史售后确认继续复用原商品累计returnStock/固定来源客户端和退款恢复协议。实际商品回执/恢复流水/清算基线联查与旧池隔离为本项目增量，yudao无同一跨代次方法，不虚构来源，原库存/事务职责作者与MIT保留。

2026-10-31：运行清算结束复用活动状态CAS/同事务审计和历史商品实际回执职责，原代次完成证明/未来售后保留为本项目增量，不虚构上游完全对应方法，原作者/MIT保留。

2026-10-31：秒杀后台清算复用既有 TenantJobAspect/TradeOrderAutoCancelJob 逐租户固定任务委托职责及原作者/MIT归属；POOL_RETURN_RECOVERY 原号恢复→池准备→单阶段清算与独立游标为本项目跨服务/原代次库存义务增量，非yudao原完整算法。见 mall-saas/docs/design/SECKILL-CLEARING-JOB.md。

2026-10-31：运行代次清算后重编继续沿用SeckillActivityServiceImpl活动/商品校验更新职责和已有失败发布恢复草稿入口；真实CLEARING_DONE/不可变基线约束的当前投影重置、草稿空档历史退款凭证为本项目增量，非yudao原完整算法。见mall-saas/docs/design/SECKILL-RUNTIME-REOPEN.md，保留原作者/MIT归属。

2026-10-31：清算进度查询沿用原SeckillActivityController.getSeckillActivity的query权限/活动商品读取/白名单响应职责及既有发布历史RR模式；明确原代次、共享七组义务谓词、封存基线加已确认历史归还为项目增量，非原版完整接口复制，保留原作者/MIT归属。见mall-saas/docs/design/SECKILL-CLEARING-PROGRESS.md。

2026-10-31：平台秒杀只读列表/审计沿用SeckillActivityController.getSeckillActivityPage权限/Service分页/响应职责及本项目平台0身份规则。精确只读Mapper旁路、配对商家活动、敏感details白名单为项目差异，无原版同名完整实现。保留原作者/MIT归属，见mall-saas/docs/design/PLATFORM-SECKILL-READ.md。

2026-10-31：平台秒杀停止沿用SeckillActivityServiceImpl.closeSeckillActivity状态更新职责、现有Expiry/stopInitialEpoch父锁和原任务缓存关闭。原平台代次版本回执/审计来源及目标上下文为项目增量，非原版完整流程。保留原作者/MIT，见mall-saas/docs/design/PLATFORM-SECKILL-STOP.md。

2026-10-31：消费者秒杀HTTP沿用AppTradeOrderController结算/创建的校验和当前会员Service委托职责，原报价回执/异步受理/本人结果为已有项目协议，本轮仅接精确HTTP/Gateway并保留默认关闭。保留原作者/MIT，见mall-saas/docs/design/SECKILL-MEMBER-HTTP.md。

2026-10-31：商家运行HTTP沿用SeckillActivityController启停权限/Service委托职责，直接复用当前EpochMaterialization/InitialRuntime/RuntimeLifecycle服务；原固定许可协议为项目增量，未声称复制原版同名协议。保留原作者/MIT，见mall-saas/docs/design/SECKILL-RUNTIME-HTTP.md。

2026-10-31：本店会员活动浏览沿用AppSeckillActivityController App分页/详情销售响应职责、既有活动和规格结构。当前发布审计/代次/会员店铺校验为项目差异，本段未复制原匿名时段缓存，匿名浏览仍待。保留原作者/MIT，见mall-saas/docs/design/SECKILL-MEMBER-BROWSE.md。

2026-10-31：公开秒杀浏览沿用AppSeckillActivityController.page/get-detail的PermitAll与销售投影职责；本人申请分页沿用AppTradeOrderController.getOrderPage登录用户分页职责。实际Host/原system入口解析、专用只读服务凭据、TenantUtils上下文恢复及原申请投影为本项目增量，非原版同名完整跨服务流程，保留原作者/MIT。见mall-saas/docs/design/SECKILL-PUBLIC-HISTORY.md。

2026-10-31：普通商品前台目录择取AppProductSpuController.getSpuPage/getSpuDetail的PermitAll/上架/SKU/销售投影职责及App分页和响应VO、ProductSpuMapper固定排序。适配当前schema/真实Host、只读服务身份、租户RR、白名单字段和筛选项，不复制虚拟销量/浏览量/足迹，不声称上游完整ES实现。保留原作者/MIT，见mall-saas/docs/design/PRODUCT-STOREFRONT-READ.md。
