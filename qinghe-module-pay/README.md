# qinghe-module-pay（DEC-065/066）

2026-10-29：本批是支付单创建/查询和trade绑定的源码，不是实际付款能力。默认回环8086、独立qinghe_pay，api/server与现有框架同构；未启动或构建。

- 来源：本地yudao-module-pay的PayOrderStatusEnum、创建/响应DTO、PayAppDO/PayOrderDO、PayOrderServiceImpl.createOrder及原trade.createPayOrder职责。枚举直接复制，适配JDK/common工具并修正array()原空数组；其他只择取当前普通订单需要的字段/方法。不导入转账/多支付渠道/统计控制台或一套新基础框架。
- 应用按tenant+appKey唯一，当前固定qinghe-trade；不能共用其他商家的配置。pay_app目前0行，无自动开通、渠道账号/密钥或支付应用种子。未配置返回503；停用禁止新建，已存在支付账的原号重试/查询仍可读取，不重开已关闭单。
- 创建req只有可信trade从主订单提供的会员、业务订单号、标题、整数分金额和原截止时间。原业务唯一键tenant/app/merchantOrderId；并发INSERT IGNORE后锁读原单，比对user/type/price/subject/expireTime，变参409。时间过期不伪造渠道已关闭，不因重试续期。Long金额兼容本项目账本；后续具体SDK须验证渠道金额上限，不能静默截断成Integer。
- 两个精确内部POST：/internal-api/pay/order/create、/internal-api/pay/order/result。复用TradeCommandAuthenticationFilter与固定authority；TRADE_PAY_SERVICE_TOKEN只在trade/pay配置，默认空拒绝，独立于库存/券凭据。不恢复普通会员为服务身份，不经Gateway，无公众创建/通知成功入口或任意回调URL。
- trade在确认主单/准备CREATED后从持久订单构造RPC，调用在事务外，共用1s连接/20s读取、16并发、无自动重试/重定向/Feign日志；回包核对租户/会员/业务单/金额/期限。PayOrderService.create仅返回WAITING账，不发起第三方付款。超时后按原业务号查询，不删除原账或生成新单。
- TradeOrderPaymentService.prepare仅内部方法：本人原订单→远程支付账→短RC主单行锁/CAS绑定pay_order_id与同事务PAY_BIND日志。渠道成功/退款结果还没有同步回订单；不能从创建账推断已付款。远程账已提交但本地绑定未知时保留原账重试，正式订单不走创建失败资源释放。
- 零元订单不创建渠道单，在本地RC事务由status0/payStatusfalse/price0/未过期/版本条件转status10/payStatustrue并写FREE_PAY日志；重复不再转换。不称真实渠道付款，也没有伪造支付单号。付款截止沿用主单创建+30分钟，绑定不改期限。

SQL按DEC-046预检执行pay/V001及trade/V007：新库utf8mb4/utf8mb4_unicode_ci，pay_app8列/2索引/4启用CHECK、pay_order15列/4索引/6CHECK均0行；原订单日志约束兼容扩展PAY_BIND/FREE_PAY，原订单/日志0行不变。没有修改数据库账号权限或真实业务行，不重复执行已落地脚本。证据../../mall-saas/docs/evidence/payment-core-schema-created.json。

静态核对20Java词法/显式内部导入、32POM XML/内部依赖无环、23SQL参数、8/15模型实际列、3YAML及精确路由认证清单。不代表编译、Spring装配、真实并发/事务/资格期限或支付测试通过；本批没有Maven/JUnit/guard/构建/启动/HTTP/业务支付写入或实际秘密配置。

下一批：渠道配置与客户端/支付尝试，先显式隔离Mock再一个沙箱，提交/查询/验签通知/安全关闭/退款及trade状态收敛；之后创建/订单/付款公共HTTP与前端、MQ/任务和实际验收。外部签约与密钥不可编造，Mock不能冒称沙箱。来源及目标摘要见../../mall-saas/docs/evidence/yudao-payment-order-core-port.json。

## 2026-10-29 支付宝扫码沙箱客户端（DEC-065/066）

本批从本地yudao直接复制退款/展示枚举及四个渠道DTO，择取PayClient、配置/工厂/Abstract模板、AbstractAlipayPayClient与AlipayQrPayClient的支付/查询/验签/退款方法。保留原作者与MIT；未移植转账、钱包、微信和多渠道页面。SDK锁定与本地yudao相同的4.40.1014.ALL；没有运行Maven解析/编译，32个POM仅XML解析。读取官方Maven的固定版本sources.jar核对6个源文件的方法名，未安装SDK或发起支付；不能把源码签名核对当二进制依赖/装配通过。

- PayClientFactoryImpl仅注册创建工厂，调用createAlipayQr才校验配置并初始化SDK；当前没有渠道配置服务调用该方法，也没有注册真实客户端。配置只接受固定HTTPS沙箱网关、RSA2、明确appId/sellerId和公钥/证书模式，不接受$class类名反序列化。私钥/加密键不序列化，配置与请求/结果不生成包含字段的toString；本批不启用可选AES内容加密，不生成/读取秘密，不允许真实资金网关。
- SDK使用1秒连接/10秒读取，进程内共享16调用额度，无应用层自动重试。禁止数据库事务内调渠道。SDK/验签异常翻译成固定待确认异常，不保留异常原报文/通知原文/完整响应，也不打印请求/密钥/买家账号。
- 扫码沿用上游precreate model/request；设置原绝对timeExpire、精确BigDecimal整数分转元，返回原业务号/二维码/金额，核对code=10000与原号。客户端未收到明确成功不能把支付单改CLOSED。渠道单笔金额上限/SDK传输兼容与实际证书仍须沙箱实测，拒绝不能偷偷截断Long金额。
- 主动查单沿用上游query，回包校验原号并投影实际金额/时间。查无单或技术错误抛待确认，不证明没有在途提交。TRADE_CLOSED也可能由已付全退引起，本批保守不将查单/通知中的该状态当未付关单证明；未来必须结合本地尝试/支付账和退款账收敛。
- 通知先RSA2验签，再核对固定appId/sellerId、币种（提供时须CNY）、原业务号、金额与付款时间。只解析同一份已验参数，忽略第二份body，不能验一份再信另一份。HTTP层尚未实现，必须在进入Map之前拒绝重复参数并限制总正文；签名解析返回结果不等于已经更新支付账/订单，也不能只凭签名跳过金额/尝试归属核对。
- 原退款创建/查询方法沿用原outRefundNo，金额精确且不超原付款。未拿到可核对的成功时间/金额或技术失败返回WAITING/待确认，不推测原退款不存在或盲换号重打。不依赖支付宝退款通知确认，仍需持久退款账/累计金额占用与任务查询接入。
- 显式补closeOrder：调用alipay.trade.close，只有code10000且原业务号一致才返回关闭回执。失败/超时保留未知；这一回执只有与后续持久提交/关闭屏障及所有尝试核对一起使用，才可支持释放库存，不能直接给浏览器调用。

15Java词法/显式内部导入、抽象/覆盖方法清单与32XML静态核对；没有SQL/建表、编译/测试/启动/HTTP或支付网络操作。工厂没有无界全局缓存，后续可信渠道服务应按tenant/channel配置版本持有客户端，不跨商家复用密钥；当前不宣称渠道配置/尝试/回调持久化完成。

下一片段接持久渠道/支付尝试及提交-关闭屏障，再接固定通知入口/验证后入账、trade结果同步与安全关单；显式隔离Mock和一个沙箱的运行验证仍待。支付应用0行、密钥空，前端仍不能付款。证据：../../mall-saas/docs/evidence/yudao-alipay-client-port.json、alipay-sdk-source-signatures.json。

## 2026-10-29 持久渠道与单次支付尝试（DEC-065/066）

择取原PayChannelDO、PayOrderExtensionDO与submitOrder“先存extension，再在事务外调客户端”的职责，新增PayChannelProperties/Service和PayOrderAttemptService，沿用现有工厂与SDK，不造新支付框架。本批一笔支付账只登记一个渠道尝试，tenant/order唯一，P+UUID固定outTradeNo。原提交超时后查询原流水，不再生成第二个尝试或换渠道；若二维码返回前进程中断，当前查询不能重新拿回未保存二维码，不盲重发，可查询付款/执行关单，后续公开支付前须处理该体验边界。

配置表只有app、固定alipay_qr、公开应用/收款方、HTTPS通知URL、configRef和版本；秘密留外部配置qinghe.pay.channels.credentials.<ref>，Binding必须包含同tenantId/channelId/version及typed AlipayPayClientConfig。默认空；本批没有登记配置或接收密钥，也不把配置返回浏览器。历史查单/关单可用停用渠道，但版本必须与原尝试相同；当前无渠道修改接口，后续更新应保留原配置引用，不静默用新商户查询旧账。不引入全局无界客户端缓存，工厂按可信配置创建，后续确有调用量再优化。

内部submit(orderId,trustedUserId,channelId)仅供后续已确认付款资格的可信调用者：先校验凭据/初始化客户端，在短RC父支付行锁下检查WAITING/未closing/未到期、应用渠道启用及归属/配置版本，保存SUBMITTING和固定no；提交后调用二维码下单。返回WAITING则保存展示内容；查询可收敛SUCCESS并在同一事务更新主支付账。没有新增HTTP、Gateway或从浏览器直接接受userId/支付金额；目前新Service尚无外部调用者。

close先锁主单并持久closing=true，后续submit不能再登记或发送新的尝试；无尝试可在同事务关闭（从未授权渠道提交）。已有尝试则置CLOSING、事务外用固定no显式关渠道，只有渠道明确成功才将尝试/主单同事务置CLOSED。已付款优先返回原支付状态；关闭RPC超时/查无单不释放资源。已发起且在途的同no提交与渠道close的竞争仍须沙箱验证渠道禁止关闭后重开，不能只靠Java锁声称远程原子性。

已闭/晚于原绝对期限的成功结果写REVIEW，不把原单复活为可发货；正常成功时间/金额/渠道流水须匹配原账。晚到查询结果不能降级SUCCESS/REVIEW或把CLOSING恢复WAITING。现阶段回调HTTP/重复参数拒绝及验签后持久入账尚未接，需后续与此同一父锁/状态协议整合，不能宣称异步支付通知已通过。退款账、trade支付成功/关闭同步及库存/券释放仍待；close仅返回支付账事实，不直接回补交易资源。

pay/V002已按DEC-046预检执行：pay_channel10列/3索引/5启用CHECK、pay_order_extension13列/5索引/6CHECK，均0行；pay_order新增closing（现16列），兼容渠道秒精度时间使成功时间最多比本地微秒创建时刻早1秒。成功时间还需通过原号/金额/渠道及上限检查，此精度容差不延长付款截止。原app/order仍0行，无账号/应用/渠道/业务种子。脚本已落地不重跑；见payment-attempt-schema-created.json。

本批12Java词法/显式导入、13SQL参数作用域、32XML及16/10/13模型实际列静态核对；无编译/Maven/JUnit/guard/启动/HTTP/支付或关单/真实锁回滚验收。下一批固定通知入口/验签落账及trade结果同步，再接受保护支付/订单HTTP与前端、恢复任务和实际Mock/沙箱验证。证据：../../mall-saas/docs/evidence/yudao-payment-attempt-port.json。

## 2026-10-29 通知入账、订单付款同步与受保护关闭（DEC-065/066）

已补固定POST /public-api/pay/notify/alipay/{channelId}：只接URL-encoded UTF-8正文、流式上限64KiB/100字段、拒绝查询串和重复解码键；该精确通知跳过普通会员Bearer恢复，仍须渠道RSA2验签和原应用/商户/金额/流水核对。按channelId窄范围忽略租户读取配置，验签后才定位原尝试，并在可信渠道租户内复用已有record父锁/同事务入账；成功返回success、错误返回failure，无通知原文日志。SDK验签接收可变副本，保留原参数供后续app/seller核对。异常晚付保存REVIEW并由API投影reviewRequired，不能用原CLOSED状态释放资源。

trade新增内部TradeOrderPaymentSyncService：事务外用专用服务凭据重新查支付账，核对原用户/金额/期限/付款时间/退款额，短RC锁主单、CAS确认和SYSTEM来源PAY_CONFIRM日志同提交。收到支付通知只完成pay库入账；尚无pay到trade持久事件/自动同步任务，不能称已自动回写订单。

新增精确服务POST /internal-api/pay/order/close（与create/result共用既有专用trade认证，未经Gateway）。请求复用固定应用/原业务订单/本人/金额/期限字段，全部从trade正式主单构造。PayOrderCloseService先通过独立事务beginCloseOrder永久停止提交，再在事务外查原渠道尝试/必要时显式关渠道，最后重读主账。已付返回原付款事实、REVIEW拒绝未付关闭、通信未知不伪造CLOSED。不依据当前时间或一次查无单释放资源。

关闭先到且尚无支付账时，ensureClosed使用同tenant/app/merchantOrderId业务唯一键创建status30/closing=true原账；不需要存在渠道/秘密配置，但必须存在原支付应用。若create先赢，关闭锁原账置closing并处理原尝试；若close先赢，晚到create只能返回同一已关闭账。请求变更用户/金额/标题/期限仍409；停用应用允许关闭历史，缺应用503不能当关闭成功。既有pay_order时间CHECK已支持过期的CLOSED登记，无新支付SQL。渠道关闭后同outTradeNo不可重新付款的实际语义仍须沙箱验证，在途请求不能只靠本地锁宣称远程原子。

本批trade/V008付款日志、V009取消进度DDL已预检执行；pay应用/渠道仍0行、实际凭据未配置。19Java词法/显式导入、29SQL参数范围、32POM XML及trade_order28字段/实际列核对，仅静态检查；未编译/测试/启动/HTTP/支付/关单/并发验收。历史manifest仅代表相应版本；本次相关源码摘要以yudao-payment-close-cancel-port.json为准。

下一片段：确认取消后按原订单回补库存/退券及持久完成回执，然后持久事件/任务恢复、公共下单付款/取消及前端、实际Mock/沙箱和履约退款。不能将已有客户端退款方法称为完整退款账。

## 2026-10-29 恢复任务主动查单接线

新增精确服务POST /internal-api/pay/order/synchronize，沿用trade专用身份/唯一可信租户，复用getOrder读取固定业务原账与既有attempt.query；渠道调用在事务外，只有原签名/金额/流水核对通过才经原record入账，再返回支付账投影。trade付款同步改调此入口，补漏通知；原result保持只读账本语义。无账返回null不能当关闭证明，REVIEW仍拒绝履约/资源释放；无Gateway新路径、无新支付SQL或凭据。尚未实际查渠道/启用XXL；最新相关源码以yudao-order-recovery-job-port.json为准。
