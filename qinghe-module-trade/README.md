# qinghe-module-trade

## 2026-10-30 自动收货任务接线增量

既有 `tradeOrderRecoveryJob` 新增固定动作 `AUTO_RECEIVE`：按可信单租户参数，发现首次发货满14天且无进行中售后的原单，登记已有恢复表并调用 `TradeOrderFulfillmentService.receiveBySystem`。复用原唯一键、短事务租约、有限异常退避及人工重放，不新建扫描/重试平台。扫描后新受理售后由订单父锁内复查暂停，正常等待归还一次预算，后续沿用首次发货日期；买家已收货只结束恢复动作，不再写收货事实。

V015仅扩展原recovery/replay两项kind CHECK，既有数据保持。过滤参数可用kind=AUTO_RECEIVE。XXL仍默认关闭，handler仍为 `tradeOrderRecoveryJob`，参数仍为单个正租户编号；实际XXL配置/所有租户任务覆盖尚待，不自动配置凭据、启动执行器或登记外部任务。当前只做静态检查及SQL元数据核对，没有编译/测试/任务或业务HTTP执行，不能称自动收货已运行。历史段落“四类恢复”由本段覆盖为五类。详见 `../../mall-saas/docs/design/ORDER-FULFILLMENT.md`。

当前从本地yudao-cloud择取配送模板主/计费/包邮三模型和查询Mapper、按件/重量枚举及模板校验职责。
原作者与MIT许可在D:/aaa/a-yudao/mall/third-party/yudao-cloud；不包含尚未移植的订单/售后/结算功能。

独立Servlet应用TradeServerApplication默认127.0.0.1:8083，数据库qinghe_trade。
QINGHE_DB_PASSWORD使用已有用户环境变量；QINGHE_TRADE_DB_URL、QINGHE_DB_USERNAME可覆盖开发默认。
SYSTEM_BASE_URL默认8080；TRADE_BIND_ADDRESS/TRADE_PORT可覆盖监听。local按NACOS_NAMESPACE/QINGHE_MALL组注册。

内部GET /internal-api/trade/delivery-express-template/validate?id使用原Bearer，核对正数商家、product:spu:create/update、真实同租户模板/启用/有效计费区域。
返回id/tenantId/authVersion/chargeMode，不表示具体地址已可配送或运费已核价。没有Gateway外部路由。
主从表在同一只读快照读取；显式默认区域规则与省市区无重叠；Decimal计量和金额边界；原样复用yudao地区CSV。

V001已创建新库/三张空表，不插默认模板或费率，不改DB账号权限。不要重复执行。
模板管理HTTP/前端及运费计算内核已在后续片段补齐；仍无会员地址/结算/订单HTTP。商品保存没有合法模板时会拒绝。
尚未编译、启动、JUnit/Mapper/JSON/RPC/事务/浏览器验证。

后续用户自行打包时需包含trade-server及product/infra/system/Gateway；本批未执行以下命令：

```powershell
# D:/aaa/a-yudao/mall
mvn -pl "qinghe-module-trade/qinghe-module-trade-server,qinghe-module-product/qinghe-module-product-server,qinghe-module-infra/qinghe-module-infra-server,qinghe-module-system/qinghe-module-system-server,qinghe-gateway" -am "-Dmaven.test.skip=true" package
java -jar .\qinghe-module-trade\qinghe-module-trade-server\target\qinghe-module-trade-server-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

来源/当前边界见D:/aaa/a-yudao/mall-saas/docs/design/PRODUCT-CATALOG-REUSE.md，证据trade-delivery-schema-created.json及yudao-product-spu-delivery-save-port.json。

## 后续配送管理/计算片段（2026-10-28，源码）

8个精确后台接口在/admin-api/trade/delivery/express-template，create/update/delete/get/list/page/list-all-simple/area-options；Gateway仅登记对应方法路径。
CRUD为当前商家精确权限/经营资格、父锁/expectedVersion及同事务审计；规则保留id，新建与删除按差集，允许安全切换默认规则。
模板删除后关联商品需换模板才能新核价，不回写既有订单快照。V002已创建成功审计空表，未插模板或费率。
商家真实页面/admin/shipping/templates；system V019新增5权限资源，未扩旧套餐/角色，分配后导航显示。

TradeDeliveryPriceCalculator是服务端内核：合并同模板计量、先判配送区域再判包邮、Decimal向上取整续费、余数/skuId稳定分摊，输出费率/区域/版本快照。
包邮的金额是本模板商品折后实付款，件数门槛不随计费方式变为kg，多个已配置门槛同时满足；没有全局免费兜底。
内核不直接接受浏览器价格/重量，尚未接会员本人地址、商品/优惠权威事实、报价标识和下单/退款协议，不称完整结算。
配置qinghe.trade.delivery.max-quote-lines默认50、max-item-quantity默认999；实际结算入口仍需校验。

本片段仅静态检查/格式化及授权SQL核对，未编译/测试/启动/HTTP/配置或计算运行验证。
详情D:/aaa/a-yudao/mall-saas/docs/design/DELIVERY-TEMPLATE-REUSE.md。


## 2026-10-29 当前会员购物车

- 直接复制原CartDO/三请求VO并择取原CartServiceImpl、CartMapper和AppCartController职责；本地适配原Bearer/实际唯一Host/本店MEMBER、原系统店铺解析、正整数/归属白名单与资源配置。商品库仅提供上架SPU+启用SKU的有界最小快照，不套用员工owner范围、不输出成本/员工/条码/统计。RR快照拼接SPU/SKU，失效项缺失则明确标注，不冒充网络故障为商品失效。
- 8个精确HTTP在/app-api/trade/cart：GET list/get-count/product-options，POST add，PUT update-count/update-selected/reset，DELETE delete。全是authenticated MEMBER端，携store编码；内部product API不经Gateway。新4网关route保留实际Host并清理Forwarded/X-Forwarded-Host/X-Original-Host，总185对/61route。
- 每会员父行trade_cart_user串行写，所有写携整个购物车expectedVersion，RC10秒内完成行修改和版本递增。相同SKU活动唯一约束兜底，合并后总量须不超实际快照库存/配置；同一旧版本重试不会再加数量，返回冲突后重读。此为乐观版本协议，不是每请求成功回执，未知结果不能声称已确定哪个并发请求成功。
- 默认100种SKU、每SKU999件、订单50行/报价300秒；checkout配置可调整至保护上限200/9999，TTL30..900秒。Cart读最多201条以识别越界，不无界查询或自动清除；配置降低不擅自删历史项。订单行/TTL目前是后续报价准入配置，尚无报价持久流程。
- 商品/入口RPC全部在trade本地事务外，写锁后复查当前本地身份期限和购物车版本；它们是时点准入而非跨服务原子撤权/库存锁。购物车不扣库存、不保留当前价，列表用当前商品库数据。下单仍须最终重读核价和数据库条件库存扣减，不能把购物车可购当下单成功。
- SKU换规格仅同SPU，用户显式确认，若新SKU已在车内则合并数量（原版覆盖目标数量按B05改正）；不自动换SKU。缺货/停用/下架保留无效项，用户可删/取消勾选，未停用店铺才可新增/改量/勾选/换规格。全部批量id必须归当前本人，混入他人项整批拒绝，无部分成功。
- V003只新增trade_cart(14列/3索引/3启用CHECK/1活动SKU生成列)与trade_cart_user(5列/1主键/2CHECK)，两表0行，配送旧表行数保持，无账号/商品/购物车种子。原秒杀V002未执行。模型非生成列与实际表静态核对。
- MemberCartPanel沿用现有样式，真实SKU搜索选择、添加/数量/勾选/换规格/合并/删除确认；身份/读取代次丢弃旧响应。成功修改后重读，失败/未知结果阻止后续写直到人工重读核对，不自动重放；不把读到旧版本声称原调用未执行。不缓存价格/购物车到额外存储，不失败回落原型；真实壳不显示演示包邮/电话/虚构品牌。商品图展示/公开目录仍待后续接入，当前规格选择显示真实名称与规格。
- 只静态词法/显式本地导入、XML/依赖无环、固定SQL参数、DO列、TS/Vue/YAML语法及route/security核对，未编译/typecheck/build/Maven/JUnit/guard/启动/HTTP/浏览器或并发事务验收。购物车结算按钮目前禁用；下一批本人地址/券RPC、价格优惠运费合成与持久报价，后续订单/支付/售后完整恢复协议保留。证据：../../mall-saas/docs/evidence/yudao-member-cart-port.json。


## 2026-10-29 当前结算报价（源码已接线，未运行验收）

POST /app-api/trade/order/settlement 只接 requestId(UUIDv4)、cartVersion、addressId、可空couponId，store来自当前商城。GET /app-api/trade/order/settlement-result?requestId 查询本人原成功报价；未查到不证明原请求没有在途。

后端读取选中购物车、实际商品和本人地址/券，先优惠再按折后金额计算运费；保存前短RC事务锁购物车并核对配送版本。原请求不同内容409，同内容返回原绝对有效期。默认300秒且不超过券到期，最多10份有效报价；只在下次保存时有界清理失效私人快照，定时留存治理尚未接入。运费内核保护默认200行/9999件；消费者仍默认50行/999件，可配置但不得超过内核。

V004已创建报价表13列/5索引/5CHECK/0行，不重复执行。页面现在可从购物袋进入checkout选择地址/券、计算并查询原报价；浏览器仅额外保存请求号/地址ID/券ID/购物车版本，不持久化联系人和金额明细。无扣库存、占券、订单或扣款，提交订单仍禁用。

本批22Java词法/显式导入、29POM XML及前端/配置语法静态检查通过；未运行编译/typecheck/build/测试/应用HTTP。证据：../../mall-saas/docs/evidence/yudao-checkout-settlement-port.json。根订单准备及最终重核价、库存/优惠券保护RPC、支付售后恢复继续待实现。


## 2026-10-29 订单准备与待处理事件（源码，未开放创建订单HTTP）

TradeOrderPrepareService.prepare/get只供后续认证入口接线。创建输入只有requestId、quoteId、remark；取当前真实会员/实际商城，不接受目标user/tenant/订单号/客户端金额。先查已提交原请求，再检查新购买资格与原报价。真实商品/本人地址/券重核价在本地事务外；金额、分摊、资料版本、地址/券/店铺资格变化要求重新确认；当前库存变化但仍足够不单独判为改价。

RC事务先锁购物车版本，再锁原报价、排序配送父行。准备记录、准备商品项、报价绑定与ORDER_PREPARED outbox同事务；整个报价只绑定一份准备记录，相同请求内容返回原进度，改变内容409。准备id/商品项id将作为后续同一订单/项的稳定身份，正式表不能重新分配另一个库存/券业务号。商品和单位优惠/运费原分摊持久保存，地址仅在私有snapshot，不写outbox或日志；单号用QH+UUID，不为编号额外引入Redis依赖。

准备截止仍是原报价截止，不是付款截止。正式订单成功后的付款截止须统一为成功时起30分钟。本批不清购物车、不写可付款主订单、不调用库存/券、不发送MQ；outbox PENDING只表示待处理，发布消费/租约/恢复/告警尚未实现。后续必须接保护RPC、真实订单归属/支付证明和完整补偿，再开放创建HTTP及前端提交。库存回补不得因支付未知而执行。

V005已执行且三表为空，不重复运行。8Java词法/显式导入、11SQL参数和41模型列静态核对；未编译/测试/启动/实际创建或并发验收。证据：../../mall-saas/docs/evidence/yudao-order-preparation-port.json。


## 2026-10-29 创建阶段内部资源RPC（源码，凭据未配置）

OrderResourceClient提供deductStock/reserveCoupon/useCoupon及stockResult/couponResult，只接本租户preparationId，从持久准备记录与稳定商品项读取会员、数量、价格、版本。RR读取结束后才调用固定product/promotion origin；不读取或保存浏览器Token，不允许在外层本地事务中RPC。券仍用原cartId作为分摊行身份，库存用准备项id；二者通过持久商品项对应，不能擅自换顺序重分优惠。

product开放精确内部POST order-stock/deduct、result；promotion开放order-coupon/reserve、use、result。它们不经Gateway，专用服务认证只匹配精确POST并要求internal:trade:order-command，普通会员/员工Bearer不适用。接收方只有服务身份，不构造LoginUser；唯一正数tenant-id在认证后作为可信服务租户，finally恢复。释放、退回、作废没有HTTP入口。

环境键TRADE_PRODUCT_SERVICE_TOKEN仅配置在trade和product，TRADE_PROMOTION_SERVICE_TOKEN仅在trade和promotion；二者须独立，不复用system-member凭据或用户Token。均默认空，只关闭相应服务入口/调用，不提供开发默认秘密。本批未生成、读取或写入实际凭据。生产固定origin须HTTPS，本机调试允许回环HTTP。

客户端连接1秒、读20秒、共享16并发，无重试/重定向/Feign日志。写503/超时视为待确认，按原准备身份查询，不换号；query null不是无在途请求证明。券占用必须带原CouponQuote，锁券核价后版本/分摊不一致即拒绝，已提交回执也核对原分摊；历史释放/退回不能当成重新创建成功。

当前没有自动资源调用/事件发送消费。启用真正创建前，必须完成券取消先到的持久关闭记录、防晚到占用、受保护补偿与支付关闭证明、处理租约/恢复/告警、最终真实会员/店铺资格复核、正式主订单和支付，以及实际编译/联调。前端提交继续禁用。18Java/29XML/3YAML及路径静态检查不代表服务安全或跨服务事务运行通过，见../../mall-saas/docs/evidence/yudao-order-resource-rpc-port.json。

## 2026-10-29 订单创建失败补偿（DEC-065/066）

新增TradeOrderCreationCompensationService.compensate(preparationId)，仅内部创建流程/任务调用，无公众或后台HTTP触发。当前可信tenant+原准备记录构造所有资源身份，不接外部user/数量/金额/理由。短RC事务锁原准备行，只允许PREPARED/PROCESSING→COMPENSATING或原终态重试；提交后在事务外调用库存与券close-creation。任一侧故障仍尝试另一侧，保留COMPENSATING并返回待确认，原号重试读永久关闭回执。只有两侧都确认才在第二个短事务标FAILED，已FAILED不再重放副作用。

product新增精确内部POST /internal-api/product/order-stock/close-creation，固定CREATE_FAILED并直接复用已有release。promotion新增相同职责的close-creation；接收方服务认证与Feign清单共7个精确POST一致，不经Gateway；不开放普通关单/退款资源释放。查询或补偿不会因为报价过期而拒绝，创建扣减/占用仍校验原准备期限。并发恢复可对已FAILED重复关闭，禁止重新创建。

重要接线约束：PREPARED/PROCESSING尚无可付款订单；后续正式主订单插入和CREATED状态必须在持有同一准备行锁的同一个事务提交，付款入口只由已CREATED主订单建立。已创建/已付款订单不得用此创建补偿，必须另走支付安全关闭/退款。本批尚无正式主订单与支付，也没有创建worker/outbox发送消费器或自动补扫；尚未把该约束当运行保证。前端下单继续关闭。

14Java词法/显式内部导入、29POM XML、16SQL方法参数作用域及7路径/认证清单静态核对；仅promotion关闭表DDL及CHECK兼容扩展已执行。未编译、测试、启动、应用HTTP或真实补偿/并发/故障验证。证据：../../mall-saas/docs/evidence/yudao-order-creation-compensation-port.json。下一批继续正式订单/创建推进及最终资格复核，支付/关单随后接入，范围以DEC-066为准。

## 2026-10-29 正式普通订单创建内核（DEC-065/066）

直接复制上游TradeOrderStatusEnum（保留原状态/作者，仅适配本项目common与JDK工具），择取TradeOrderDO/ItemDO普通快递字段与createOrder/buildTradeOrder/save/deleteCart/log职责。排除积分、拼团、自提等范围外字段，金额使用已有Long整数分语义；未重做框架或引入通用Handler/流程引擎。

TradeOrderCreationService.create(request,store,preparationId)仅内部方法：实际MEMBER/Host及原准备归属→短RC锁准备行PREPARED转PROCESSING→事务外扣库存/占券/核销（原号幂等）→原Bearer资格/店铺重读→短RC写正式订单/原编号商品项/日志/清未改动购物车/准备CREATED。同事务提交的准备行屏障与创建失败补偿共用，COMPENSATING/FAILED不能再创建；CREATED重试查回原主单，不能走创建失败释放。

购物车父行先于准备行，按原cartId/sku/count/行版本条件清理；用户后来改过的行保留，删除至少一行才递增当前购物车聚合版本。原成交快照与单位优惠/运费分摊通过INSERT SELECT从准备项复制，订单/商品项id与资源账本一致，不重新核算历史优惠。收件信息取私有准备快照，不从浏览器重传或跟随地址后续修改。创建日志action固定CREATE/会员actor，后续状态日志随对应功能扩展，当前无泛化审计层。

远程明确业务拒绝才调用已有补偿；503/通信/提交未知保留原进度，不能谎称回滚。写前重读真实身份/入口版本，回调末尾检查本地身份/原准备期限以及资格读取开始到回调结束的5秒上限；此为有界时点校验，不是跨库原子资格锁，也不是已实测满足五秒撤权。RPC从不在trade行锁内。

V006已按DEC-046核对QIANG/MySQL8.0.43/qinghe_trade并执行新增trade_order（25列/5索引/7启用CHECK）、item（22列/3索引/8CHECK）、log（9列/2索引/2CHECK），均0行；原准备/购物车/报价/outbox行数0保持。稳定订单号与本人查询索引、支付单唯一约束、状态/金额/分摊约束保留，无业务/账号种子。脚本不重复执行。支付截止固定为主订单记录创建时刻+30分钟，重试不重置。

当前仅内部建单源码，无公众创建/订单查询HTTP、支付单绑定、MQ创建worker/publisher或自动恢复任务。payPrice=0也尚未接免支付确认，不把UNPAID原单称可付款或已付款；必须随下一批支付接线补齐后再开放。前端提交仍关闭。7Java词法/显式导入、20SQL参数、25/22模型实际列及29XML静态核对；没有编译、测试、启动、应用HTTP/实际建单或并发回滚验证。证据：../../mall-saas/docs/evidence/yudao-formal-order-creation-port.json、formal-order-schema-created.json。

## 2026-10-29 支付单创建/绑定前置（DEC-065/066）

新增pay api依赖与OrderPayFeignApi，直接复用现有OrderResourceClient的固定origin/服务认证/有限并发超时和错误处理。支付参数只从正式主单构造，读取准备CREATED核对同一会员；创建与查询按原订单号，不传浏览器金额/任意回调。TRADE_PAY_SERVICE_TOKEN新增环境占位且默认空，pay与trade共享此独立接收方凭据，本批未配置实际值。

TradeOrderPaymentService.prepare(request,store,orderId)内部方法已接本人订单/真实入口、事务外支付单创建及短RC本地主单行锁绑定/日志；不自动执行公众付款。零元订单不建支付单，本地一次条件确认与FREE_PAY日志；正金额只是绑定WAITING账，支付成功仍须后续可信通知/查询。绑定重试不改变原金额/截止时间，远程结果未知不可用创建失败补偿释放正式订单资源。接口返回requiresPayment表示仍需外部付款，不是渠道可用或操作授权。

trade/V007兼容扩展原日志CHECK，未改业务行。pay库及app/order表已建，应用/渠道/凭据仍未配置；来源、边界及运行待办见../qinghe-module-pay/README.md。没有编译、测试、启动、HTTP或实际支付/零元确认/并发验证。当前仍无公共创建/支付入口，前端提交保持关闭。

## 2026-10-29 付款同步与安全取消内核（DEC-065/066）

TradeOrderPaymentSyncService已接事务外查询pay账与短RC主单CAS确认：支付服务回包必须对应原订单/会员/金额/截止时间，付款时间在原期限内且无退款/REVIEW；同一付款重复确认只返回原状态，已取消订单不复活。PAY_CONFIRM为SYSTEM来源，actorId=0，与付款状态同事务。回调HTTP只负责pay入账，可靠通知/任务触发该同步尚未接。

TradeOrderCancelService新增cancelByMember(request,store,orderId)和cancelExpired(orderId)内部方法，无HTTP/定时执行器。会员路径验证本人/实际商城入口，经营暂停不阻止历史订单取消；任务路径要求可信租户及到达原payDeadline。正金额先通过专用服务closePayment按原业务号关闭pay账（无账也永久留关闭记录），已付款转付款同步，不是未付取消；只有明确CLOSED/closing且无REVIEW/付款/退款才进入本地取消事务。零元订单不造支付账，本地取消和免付确认竞争同一主单行锁/状态CAS。

取消主单status0→40、原支付id绑定、cancelType（10超时/30会员）、cancelTime和来源日志同事务；并发付款已确认则不取消，重复取消不再加日志/改变原取消原因。真实渠道查询/关单均在trade本地事务/行锁之外，失败不返回成功、不释放库存/券。不将用户提供的userId/金额/原因传给资源服务。

V009已预检执行在qinghe_trade新增cancel_type/cancel_time/cancel_resources_released，主单现28列，新增恢复查询索引和取消CHECK、兼容扩展两种来源取消日志；原订单/日志0行保持。cancel_resources_released默认false，当前没有任何代码将其置true，也尚未调用正式取消后的库存/券释放；cancel方法true仅表示主单已取消，不能解释为完整补偿成功。后续仅核对两侧原单永久回执后确认该进度，失败按原单恢复，不手改业务表。

最新相关源码/静态核对见../../mall-saas/docs/evidence/yudao-payment-close-cancel-port.json，数据库元数据见unpaid-order-cancel-schema-created.json。无编译/测试/启动/HTTP/真实取消或锁竞争验收；公共创建/付款/取消和MQ/任务等仍待。

## 2026-10-29 未付取消后的库存/券清理（DEC-065/066）

TradeOrderCancelService的会员/超时方法在主单取消提交后调用新TradeOrderCancelResourceService.release；只有清理回执及本地标记确认后才返回true。若中途故障，主单仍为已取消且cancel_resources_released=false，返回503明确待清理，不宣称原取消事务回滚；再次按原单调用会继续清理。该Service也可供后续可信恢复任务使用，当前没有调度器/公共HTTP自动调用。

清理只接当前可信tenant/orderId：读取已持久status40、payStatusfalse、type10/30的正式订单；正金额再次从支付库确认原payOrderId=CLOSED、closing=true、无REVIEW/成功/退款后，才事务外调用两侧close-unpaid。一侧失败仍尝试另一侧，全部成功后再次核对支付账，再短RC锁主单核对原取消事实，CAS置完成并同事务写SYSTEM/CANCEL_RELEASED日志。已完成直接返回，不重复写日志；提交未知保留原号查询/重试。

库存复用既有ProductOrderStockService.release(UNPAID_CLOSED)，客户端核对原订单/会员/全部商品项/数量/价格/版本和每项回补增量；正式单必须收到RELEASED及固定原因，空CLOSED记录不能冒称正式库存已回补。券复用CouponOrderService.returnUsed(UNPAID_CLOSED)，只接原核销订单，核对原券/完整历史分摊及state4/原因；未选券才允许本地null，选券后null不是完成。原券过期/作废事实、原期限和领取额度保留；旧订单重放只查自己的永久回执，不清除后来新订单的使用指针。

新增product/promotion两个精确服务POST close-unpaid，共9个资源POST与Feign/专用trade认证清单一致，无Gateway放行/新增公共路径/实际凭据。V010只兼容扩展日志CHECK允许40→40的CANCEL_RELEASED，订单28列/日志10列和0行保持，无新表/通用工作流/重试框架。当前11Java词法/显式导入、15Mapper参数范围、32POM XML及9路径静态核对；未编译/测试/启动/HTTP/实际库存/券写入或幂等并发验收，不能称端到端购买可用。证据../../mall-saas/docs/evidence/yudao-cancel-resource-release-port.json、cancel-resource-confirmation-schema-created.json。

下一片段：持久推进/任务恢复及公共订单创建、支付/取消和查询前端；付款通知自动驱动trade、二维码未知结果恢复、实际Mock沙箱与履约退款等继续保留。

## 2026-10-29 普通订单四类恢复与XXL入口（DEC-065/066）

接入新job starter，默认TRADE_JOB_ENABLED=false，不自动启动任务。直接择取原TradeOrderAutoCancelJob的@XxlJob入口与批量调用职责，handler为tradeOrderRecoveryJob；本批参数为单个正租户编号，每租户在XXL独立配置任务，建议每分钟触发。没有从外部HTTP头读取租户，也不因租户停止经营跳过既有义务。入口清理Security/Tenant上下文，TenantUtils.execute逐任务设置可信租户，finally清理，并关联本次UUID trace及orderId。自动枚举/开通新租户的任务登记仍待，不把当前显式参数方式称全租户自动覆盖。

TradeOrderRecoveryService只处理四个固定动作：PAY_SYNC主动查原渠道并同步原付款账；CANCEL_EXPIRED按原期限调用既有安全取消；CANCEL_RELEASE继续原库存/券回执；CREATE_COMPENSATE对到期PREPARED/PROCESSING或COMPENSATING调用既有创建失败补偿。准备恢复不会伪造会员Token或资格来自动创建订单；用户仍可在准备有效期内按原请求继续创建，过期后收敛失败资源。MQ异步创建/事件投递尚未实现，原outbox不冒称已发布。

本地恢复记录按tenant/kind/order唯一，NOT EXISTS+唯一键有限登记后续源记录；每次默认最多处理5条，每条先短RC锁/租约领取，再在事务外调用已有业务方法。FOR UPDATE SKIP LOCKED避免执行器相互等待，180秒租约和owner/version/未过期条件限定进度提交；超过45秒只停止领取下一条，不中断已经发出的业务调用，单条仍依赖现有有限RPC超时。重复领取/执行仍靠原订单业务幂等，租约不是远程副作用原子保证。实际批量吞吐和任务超时设置须运行验证，当前不承诺扫描SLA。

异常按15秒起指数退避、最大15分钟、单动作最多10次，执行中进程崩溃也消耗已持久领取次数；第10次租约过期转MANUAL。正常等待付款归还本次异常预算，15秒后可再查，不因正常等待把订单转人工。失败只保存固定错误码/最后XXL任务id/trace，持续MANUAL使任务handleFail并记录可检索告警。人工处理记录不删除或自动重置；受保护查询/按原因重放与审计入口仍待，禁止手改表代替恢复。

新增pay精确服务POST /internal-api/pay/order/synchronize并接既有专用trade认证/Feign；订单付款同步现在主动查询原尝试再读支付账，回调丢失可由任务补查。无尝试/无账不当关闭证明，超时/异常保留原号；无新Gateway/public路径/实际凭据。

V011已预检创建trade_order_recovery（15列/4索引/7启用CHECK/0行），原订单/准备/outbox仍0，无业务回补/付款写入。12Java词法/显式导入、33POM XML/内部依赖无环、9恢复SQL参数范围、DO15字段/实际列、现有Prettier仅解析YAML及4个pay服务路径静态核对；未编译/测试/启动/任务登记或执行/HTTP/真实锁/租约/退避/故障恢复验收。SDK helper方法只对照官方固定2.4.0源码，不称二进制依赖或运行通过。证据../../mall-saas/docs/evidence/yudao-order-recovery-job-port.json及order-recovery-schema-created.json。

下一片段继续受保护恢复查询/按原身份人工重放及必要审计，然后MQ/outbox、公开购买/支付/取消/查询与前端；履约退款、秒杀/基础ES及实际验收继续保留。

## 2026-10-29 恢复记录查询与人工重放（DEC-065/066）

新增商家精确HTTP：GET /admin-api/trade/order-recovery/page、get、replay-result、replay-history，POST /admin-api/trade/order-recovery/replay。Controller/Service均核对当前ADMIN正租户与独立query/replay资源权限，平台0/消费者不适用；响应no-store。遵守B03订单岗位当前租户范围，不新建客服派单/部门归属模型，不返回会员收件隐私/租约owner。页大小最多100、offset最多100000，固定状态/动作白名单。无独立运维前端/控制台；后续调用须显式配置套餐与角色资源，不能默认扩权。

重放body仅id、expectedVersion、UUIDv4 requestId、非空无控制字符reason（<=200字符），不接tenant/user/kind/金额/渠道/任意执行参数。先在本地事务外校验真实当前身份/权限，短RC锁当前恢复行；仅MANUAL且版本一致、无租约才把同一记录重排READY、恢复本轮10次预算，并在同一事务插入操作者/原动作/原因/前后版本/上一轮次数与错误。提交前作本地身份/到期与5秒时点复查，不在锁内RPC；不声称与远程撤权原子提交或五秒运行验收通过。

同请求号先读取已提交回执，同操作人/记录/版本/规范化原因才返回原受理/执行结果，不重复排队；异参409。不同记录并发误用同请求号，由唯一键裁决，当前重排事务整体回滚再核对原回执。操作只是排队，READY不是业务已恢复，执行器仍默认关闭；响应丢失先GET replay-result或按原号重试，不换号盲重放。

trade_order_recovery新增active_replay_id，trade_order_recovery_replay保存每轮独立结果。XXL领取/结果/到期转MANUAL与本轮历史进度在同一事务更新，保留owner/version租约栅栏；下一轮不覆盖上一轮MANUAL原因/结果。history分页只读本租户原记录，结果含固定错误码、最后任务/trace/版本，不包含原RPC正文。重放不改变原业务身份，不绕过渠道关闭/退款规则，不直接更新订单/支付/库存。

V012已预检执行：恢复表16列，重放历史20列/3索引/8启用CHECK/0行，原恢复/订单0行保持。system V025只登记3个隐藏权限资源（目录/查询/重放）和3条direct-sql审计，菜单94，3套餐/角色绑定指纹不变；隐藏目录不是已实现的OrderRecovery前端页面。Gateway新增5精确方法路径，总192，授权规则与代理路由静态一致，未开放内部恢复/平台跨租户路径。

本批12Java词法/显式导入、33POM XML、20SQL参数/动态SELECT XML、16/20模型实际列、YAML语法及192路由静态核对。未编译/测试/启动/HTTP/实际重放或任务/并发验收。证据../../mall-saas/docs/evidence/yudao-order-recovery-manual-port.json及两个DDL/资源执行记录。XXL实际配置/全租户自动登记、MQ/outbox、公开下单付款及剩余普通交易仍待。

## 2026-10-29 消费者创建/查询与结算提交（DEC-065/066）

新增本人订单HTTP：POST /app-api/trade/order/create、stop-create、prepare-payment、cancel；GET create-result、page、get-detail。无PermitAll/自由tenant/user/金额/支付成功输入；现有Bearer经实际Host/店铺校验后使用当前MEMBER，历史查询/取消不依赖canPurchase为true，身份停用仍拒绝。Gateway只增加7条精确方法路径，总199，保留实际Host/清洗转发Host；写代理响应上限120秒、无重试，浏览器写超时125秒，超时不是业务已终止。没有公开渠道submit或付款成功入口。

TradeOrderAppService直接调用既有prepare→create，同requestId/quoteId/规范化remark复用原准备和商品/金额/单位分摊，唯一键/原锁与补偿语义保留；已CREATED只返回原单，COMPENSATING/FAILED/MANUAL明确投影而不伪装新建成功。零元主单复用原prepare免付确认，正金额另由prepare-payment建立原支付账，不发起渠道扣款。创建返回preparationId/requestId/quoteId/stage/serverNow/order；仅CREATED且order非空存在正式单，payStatus独立。任何后续失败均可能发生在已保存准备/主单之后，客户端保留原号查询。

创建结果查询GET为只读RR快照，未查到返回null但不证明没有在途创建；不夹带渠道查单/取消。本人列表按tenant/user有界分页（每页<=20、offset<=100000），批量最多4000项，汇总核对原数量/实付；每单预览最多3项，不回表读取当前商品改写历史。详情仅本人订单完整原商品规格/收件快照，列表不含收件信息；不投影内部单位分摊/成本/员工字段。时间统一epoch毫秒，原付款截止+30分钟不因绑定/重试改变。requiresPayment只描述原应付事实，不宣称渠道可用。

显式stop-create解决“准备之前就被拒绝、查询一直null”的恢复出口：实际会员校验后，以购物车父锁→原报价锁串行，重新查同request准备。已存在只返回stopped=false及原结果，不取消/补偿它；不存在才将未绑定报价snapshot置null（已绑定其他请求也不再允许用原报价创建），stopped=true证明原报价已永久不可准备。迟到prepare即使已在事务外算价，也在同一cart/quote锁后usable(locked)拒绝，不会重新占资源。结束响应丢失可按原号再次结束；不更改已有订单截止时间，不清已有库存/券，不新增SQL表/通用幂等层。缺原报价/主体仍拒绝，不把一次查询空值当终止成功。

消费者取消HTTP新增渠道调用后的实际入口/身份重读及事务内本地时点检查（5秒），避免沿用很早的入口认证提交；不在锁内RPC，不称远程撤权原子或真实验收通过。备注控制字符校验同时保留VO与Service语义。

前端checkout现可确认原商品/地址/金额后创建并查询原结果。当前标签页按realm/store/tenant/member保存请求/报价ID、确认金额、空备注与结果标记，不保存收件/订单快照或凭据。先持久请求后POST，未决阻止新报价/新单，恢复不依赖购物车仍有行或优惠券仍未核销；同号继续须显式确认，不自动重发，结束未受理请求也须确认。CREATED显示尚未付款或零元已确认，取消与资源完成分别显示；实际付款入口/本人订单列表详情前端仍待，不冒称购买闭环可用。失去浏览器存储时无法自动找回原键，不能清未知记录绕过保护。

本批18Java词法/显式导入、33POM XML、26SQL参数/动态XML、199Gateway规则及两前端文件TS/Vue语法解析/局部格式化。未编译/typecheck/build/测试/guard/启动/HTTP/浏览器/真实建单或SQL迁移/业务数据写入。证据../../mall-saas/docs/evidence/yudao-member-order-checkout-port.json；上个member-order-http manifest为中断前版本，由此补齐说明。后续本人订单前端、渠道付款提交/查询/二维码恢复、MQ/outbox/租户任务登记、履约退款及其余DEC-066与实际验收继续。
