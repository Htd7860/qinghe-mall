# 优惠券服务源码复用

DEC-065，2026-10-29。当前是模板管理和成功发放内核源码，非已运行服务或完整优惠券交付。

- API/server 独立模块，默认回环 8085，自己的 qinghe_promotion；沿用现有 Servlet/MyBatis/租户/远程 Bearer 框架，没有跨库 Mapper。
- 直接择取原模板/会员券 DO、五个枚举、规则 VO、模板 CRUD/分页、发放与快照职责。保留作者及 `../third-party/yudao-cloud/LICENSE`；映射摘要见 `../../mall-saas/docs/evidence/yudao-promotion-coupon-core-port.json`。
- 满减/折扣、全部/SPU/分类、固定日期/领取后连续 N 天；相对规则按 B05 从领取时刻立即起效，只接受 fixedStartTerm=0，保存绝对期限。金额整数分，范围/额度/变体字段规范化；不引入注册自动送券、积分等级等未选业务。
- 模板管理只接受白名单规则，当前商家员工/具体权限/经营资格前置；商品范围在事务外调用 product 最小内部 API，校验真实 tenant、SPU 数据范围或启用分类。配置在 RC/10秒本地事务下共用模板父锁、版本 CAS 及成功审计。跨服务检查是当前时刻的准入，非分布式锁或取消已准入在途操作的证明。
- 模板暂停只影响新发，更新只改模板，不覆写会员券快照；只能删暂停且从未成功发放的模板。总量不能降到 takeCount 以下。
- `CouponService.takeCoupon` 是内部内核，不是授权入口。调用者须先确认实际店铺、真实会员与经营资格；营销须另查退出/批次状态，并提供 promotion 的 READ_COMMITTED 可写事务。MANDATORY 拒绝无事务调用，隔离级别另显式核对。当前还没有公开调用者，不能直接把浏览器 userId/tenantId/takeType 绑定到它。
- 单次发放锁模板、核对个人历史成功数、条件增加总成功数、保存完整券快照；额度与券同事务。总量和个人量包括逻辑删除记录，作废/过期/退回不减少成功领取次数。管理员/活动源可与公开模板共用同一额度；USER 类型仅能领取允许公开领取的模板。
- 稳定 grantKey 在 tenant/user 内唯一，同键异模板/类型拒绝；成功回执包括逻辑删除，不表示券当前仍有效。并发同键与父锁、数据库唯一键配合，调用者遇到事务失败必须在事务外查询原结果，不换键盲目再发。没有承诺持久记录所有失败尝试。
- 券状态 1未使用/2已使用/3过期延续原版，4订单占用/5作废及关联字段仅为后续协议保留。本批没有锁券、核销、退回、作废、过期任务或资金退款实现；不能通过修改模板恢复已作废券。定向活动归属、会员复查及通知也尚未接入。
- V001 与 V003 已预检执行：模板28列/3索引/9启用CHECK、会员券29列/7索引/11CHECK、模板审计9列/3索引/4CHECK，三表均0行。新建服务库，无账号/券/权限种子，无数据库账号权限变化；既有秒杀 V002 未执行。
- 当前没有 promotion Controller、Gateway 公开路由、菜单资源初始化或前端。product 新内部范围校验不经 Gateway。下一批补这些入口及真实消费者 Host/会员准入、稳定键查询和页面。
- 22份 Java 词法/本地导入、6份 POM XML/模块依赖无环、2份 YAML 语法、SQL参数/数据库元数据已静态核对；未编译/Maven/JUnit/guard/启动/HTTP/Redis/实际模板或发券/并发回滚验证。所有服务统一 Asia/Shanghai 日期语义与时钟同步仍需部署验收，原整数计数字段保留20亿资源上限，不宣称无限容量。


## 2026-10-29 商家管理端接线（覆盖上文无公开HTTP的早期状态）

- 六个精确HTTP位于`/admin-api/promotion/coupon-template`：POST create、PUT update/update-status、DELETE delete、GET get/page。继续用原permission字符串并独立核对当前商家端/tenant；更新/删除带expectedVersion，DTO白名单不返回内部租户/审计操作者。只移植本批实际使用入口，不开放无界list或平台跨租户业务写入。
- Gateway仅新增这六条authenticated路由，固定promotion origin，写请求代理响应上限30秒；总172个method/path/55个route与安全规则一致。Feign商品范围内部API仍不路由Gateway。Spring Jackson在promotion源配置禁accept-float-as-int，防JSON小数静默截成整数分/次数；仅源配置核对，未启动验证。
- V024登记商家营销目录、模板页面与create/update/delete三个按钮，新增5资源/5条direct-sql审计；管理mutex锁内核对初始化平台身份，预检冲突并核对现有套餐/角色绑定指纹不变。新功能不自动加入旧套餐/角色；平台须按现有授权流程配置相应商家，不能把资源登记称已授予业务权限。
- 真实CouponTemplates.vue沿用当前组件/CSS，含分页/状态筛选、详情、新建/编辑、暂停恢复及受限删除。金额按字符串分拆成整数分，固定时间保留秒并标明北京时间；不用浮点乘100后四舍五入。范围选择复用真实商品/分类查询，需相应查询权限；无该权限可保留已存范围，服务器仍校验真实对象和数据范围。不会自动扩大商品查询权限。
- 写回附读取版本；未知结果阻止重复写，操作者重新读取列表/详情核对，不自动重放或把失败当成功。配置新建不具备服务器operation-key幂等，本批不把手工核对称为已证明唯一创建。身份/页面代次丢弃旧响应，不保存Token/规则副本到额外浏览器存储；原prototype保持独立。
- 7Java显式导入/词法、4XML、5TS/Vue Prettier语法与YAML及精确路由静态核对。没有编译/typecheck/build/测试/guard/启动/HTTP/浏览器、实际模板或发券/并发回滚验证。当前运行产物可能仍旧。证据：yudao-coupon-template-admin-http-port.json、coupon-template-http-route-inventory.json、coupon-template-permission-resources-created.json。
- 下一批消费者Host/会员真实准入、可领取模板/本人券读取、稳定领券与成功结果查询、真实消费者页面；用券报价/锁定/核销/退回/风控作废、活动营销/任务和完整运行验收继续保留。


## 2026-10-29 消费者领券与本人券（覆盖无消费者调用者的早期状态）

- 五个精确HTTP：GET `/app-api/promotion/coupon-template/page`、`coupon/page`、`coupon/get`、`coupon/take-result`；POST `coupon/take`。均为当前MEMBER入口，匿名页面提示登录，未新增匿名模板目录。请求只接templateId/requestId，实际user/tenant/USER类型由当前可信身份给定。
- system新增内部当前MEMBER入口解析，直接复用StorefrontService，携原Bearer且再次经过真实会员资格验证；promotion从唯一实际Host提取来源，不接受JSON/Origin/转发头替代，不共享会员签发密钥。读允许历史租户身份；新领取额外要求canPurchase。此为时点准入，不声称跨服务原子撤权；入口自身停用/解绑仍拒绝访问。
- 领取使用REQUIRES_NEW/READ_COMMITTED/10秒，直接委托已存在MANDATORY发放内核。稳定grantKey为claim:UUID，用户/租户内唯一；先查原成功再判新领资格，唯一键异常在事务回滚后恢复原回执。found=false仅表示暂未看到成功，不意味着其他请求不在执行，不能换号盲重领。当前不存在单独持久失败请求表。
- 本人券按tenant/current user双归属查已发快照，不跟随模板暂停或修改；结束时刻不可用，未使用到期在读投影显示过期，不冒称过期任务已执行。模板页一次分组读历史成功量，逻辑删除不退领取额度；分页上限20/页码10000。浏览器响应只含券面、状态及归属模板，不含用户/租户/发放键/订单/审计元数据。
- Gateway仅新增5条authenticated方法路径，总177对/57route；消费者两路由必须PreserveHostHeader并清理Forwarded/X-Forwarded-Host/X-Original-Host。新内部店铺API不经Gateway。
- MemberCouponPanel接真实领券中心、本人券筛选/分页/详情；原型不变，路由跳过未实现commerce/views。当前标签页按会员/店铺保存每模板一个待确认UUID（上限100，不淘汰未决请求），可刷新查询原结果或显式原号重交；丢失存储/关闭标签页后不承诺自动恢复原键。仅恢复成功回执，不把它当当前可用性，也不提供未实现结算按钮。
- 本批没有SQL/真实领券或账号/权限写入。仅源码/语法核对，编译、typecheck/build、测试、启动、真实Host/跨店/领取重试/并发锁回滚和浏览器验收均未运行。来源与摘要见`../../mall-saas/docs/evidence/yudao-coupon-member-http-port.json`。下一批用券核价/订单占用/核销/退回/作废，再接购物车结算、交易、营销任务及全范围运行验收。


## 2026-10-29 用券核价与订单状态领域

- 直接复制原CouponRespDTO，修正templateId为Long、加tenant/authVersion；择取原TradeCouponPriceCalculator范围筛选/门槛/折扣实付比例和CouponServiceImpl.useCoupon/returnUsedCoupon职责。共享CouponPriceCalculator放promotion-api，trade计算器独立匹配当前tenant/trusted member；不依赖其他server模块。
- 仅权威商品行（无运费）参与核价；一张券、普通订单、起始包含/结束不含，按SPU/分类/全店匹配，门槛只累加匹配行。保留原百分比实付截断语义，折扣上限与适用商品金额共同封顶，允许券抵完商品款，避免原Integer乘法溢出。BigInteger只用于比例乘法；行分摊取整后按余数降序、稳定lineId/SKU补1分，单位按base/remainder编码（前remainder个稳定单位加1分）。相同SKU须合并；当前内核固定上限50行/999件，后续结算配置接入仍待。
- CouponOrderService是内部领域Service，无本批HTTP/Feign公开写入口。trusted user/order/行数据须来自trade权威准备记录，未来调用者须先完成固定服务身份及订单归属、关闭/全额退款事实校验，不能把浏览器JSON字段直接绑定进来。占用/核销/释放/退回/作废使用REQUIRES_NEW、RC、timeout10、rollbackFor Exception，本地锁内没有RPC；没有跨服务原子性声明。
- 占用锁券后写永久tenant/order唯一绑定、原请求数值摘要和核价/分摊JSON，同事务写状态审计。摘要按排序后的line/SPU/SKU/category/qty/price生成，含原tenant/user/order/coupon；同订单换券/换商品/改价拒绝。多个请求同券仅一订单能占用，唯一订单键还防不同券争抢同订单；唯一冲突让整个本次事务回滚，调用者在事务外查询原订单结果。查不到不代表原调用不在途，不能换新订单盲重试。
- 核销在最终创建订单前调用，仍核对券到期、作废、原占用订单/版本；无需等付款才消耗。准备释放仅接受CREATE_FAILED/UNPAID_CLOSED，原已核销券只在UNPAID_CLOSED/FULL_REFUND退回，部分退款不得调用；这些理由必须由后续trade/pay/aftersale实际证明，本Service没有自行验证外部付款事实。不能因为30分钟到时就当已安全关闭并退券。
- 退回清当前使用/占用指针但不延长有效期、不新发券、不释放领取额度；若已到期置3，作废保持5。永久订单绑定保留成功阶段1占用/2核销/3已释放/4已退回，旧订单重复请求只返回原阶段，不触碰这张券后来的新订单；原订单终止后不能再次核销/换券。作废保留原订单指针待实际订单收敛，不自动退款或取消。
- 新binding/audit表不软删除，不能按短TTL清掉仍可能被重试的订单结果；券引用/版本成功审计同本地事务。模板历史use_count字段本批未作为权威营销/净核销计数更新，后续统计应基于经验证的券/订单历史，不把它当现有准确指标。
- V004在既有目标空库预检执行一次：promotion_coupon_order 13列4索引6启用CHECK；promotion_coupon_audit 13列3索引4启用CHECK，均0行，原业务表与数据保持。无账号/券/订单/审计业务种子。既有秒杀V002仍未执行。
- 只做11Java词法/显式导入、29POM XML/依赖无环、12固定SQL参数和DO列映射静态核对及DDL元数据检查。未编译/Maven/JUnit/guard/启动/HTTP/真实券操作/事务锁与回滚验收。原网关177对保持；购物车/权威商品与地址RPC、结算报价和真实订单/payment/refund调用者仍待，不能称消费者已经能下单。证据：`../../mall-saas/docs/evidence/yudao-coupon-order-pricing-port.json`。

## 2026-10-29 创建失败补偿（DEC-065/066）

订单写入统一先锁tenant/order gate，再操作原绑定与券。reserve/use在gate已关闭时拒绝；取消先到也持久登记关闭，不伪造报价/占券记录。closeCreation只用于trade尚无可付款订单的创建失败：未占券只关闭；已占用释放为state3；已核销退回为state4，reason固定CREATE_FAILED。沿用原恢复SQL/审计，保留期限/作废事实；重复关闭只读原回执，不触碰后续新订单使用的券。普通未付关单/全额退款仍沿用原领域方法和外部支付事实要求，不增加这两类HTTP。

新增内部POST /internal-api/promotion/order-coupon/close-creation，固定trade服务authority，参数只有原user/order/coupon；普通Bearer和Gateway不能调用。原3条加此条共4条服务路径，服务密钥仍未配置，不称实际接通。

promotion V005已按DEC-046预检执行：关闭表8列/1主键/3启用CHECK/0行；原订单state4退券原因CHECK扩展接受CREATE_FAILED，原业务表各0行且未改数据。历史绑定只向新gate表回填，当前0行。DDL隐式提交，不重复运行脚本；异常中断应检查已落地对象。证据：docs/evidence/coupon-order-close-schema-created.json（正式工作区）。没有真实券业务写入或锁/回滚运行验证。

## 2026-10-29 未付正式订单退券接线

新增精确服务POST /internal-api/promotion/order-coupon/close-unpaid，沿用专用trade服务认证，固定调用现有returnUsed(userId,orderId,couponId,UNPAID_CLOSED)，不接自由退券原因或退款证明，不经Gateway。正式订单创建时已核销，本入口只退原核销记录；已有state4及同原因回执直接返回，不触碰后来订单的使用状态。原gate永久关闭，禁止晚到占用/核销；原有效期、过期/作废和领取计数规则不变。无新优惠券SQL或实际券写入，未编译/测试/启动/HTTP验证；接线及来源见mall-saas/docs/evidence/yudao-cancel-resource-release-port.json。
