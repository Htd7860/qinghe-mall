# qinghe-module-product

当前移植范围来自本地yudao-cloud，原作者/许可见../third-party/yudao-cloud/，不修改上游目录。

已写：分类、品牌、属性项、属性值及SPU/SKU的模型和查询Mapper；分类/品牌/属性VO，核心错误码和状态枚举。
原版结构保留，按本项目B04增加可信tenant、销售/展示属性、状态和资料版本，移除已排除的积分/分销业务。

本机独立数据库qinghe_product的六张业务表均为空（另有配置锁、审计及库存流水表）（以执行证据为准），无账号权限更改和商品初始化：

- V001__create_product_catalog.sql：四张基础资料表，已执行，不要重复运行覆盖库。
- V003__create_product_spu_sku.sql：SPU/SKU表，已执行，无业务数据。
- 现有V002__create_seckill.sql：早期编写，未执行，不因V003落地推断已应用。

基础资料四组Service已接真实重名/树/引用检查、版本更新与审计；属性改名同步当前SKU/展示属性，分批只改元数据。
V004已创建租户内低频配置锁表product_catalog_mutex及审计表product_catalog_audit，两表为空，不串行化高频库存。
Security starter增加普通经营资格RPC，system内部只核对当前Bearer商家，不接受外部目标租户。
四组Controller已按yudao移植，24个精确HTTP method/path，经Gateway转发；商家管理页新增分类/品牌/规格入口。
ProductServerApplication使用独立Servlet进程，默认127.0.0.1:8082；数据源qinghe_product。
QINGHE_DB_PASSWORD沿用用户环境变量；SYSTEM_BASE_URL默认8080，INFRA_BASE_URL默认8081；PRODUCT_BASE_URL供Gateway指向8082。
默认不注册Nacos，local profile按现有NACOS_NAMESPACE/QINGHE_MALL组注册；没有更改Nacos服务端配置。
未编译、启动、运行JUnit、真实Mapper/JSON/事务/HTTP/浏览器验证；没有商品发布与库存HTTP；库存Service见下方后续片段。

## HTTP和权限

/admin-api/product/category：POST create、PUT update、DELETE delete、GET get/list。
/admin-api/product/brand：POST create、PUT update、DELETE delete、GET get/list/page/list-all-simple。
/admin-api/product/property及property/value：各POST create、PUT update、DELETE delete、GET get/page/simple-list。
路径均有对应product:category/brand/property的query/create/update/delete权限；选项列表也显式授权。
所有更新与删除须expectedVersion，删除还须id；无平台代操作入口，Service拒绝tenant0。
system V018新增商品目录及三组页面/操作共13资源、13条明确SQL来源审计，不扩现有套餐/角色。
商家在套餐和角色选取完整父链资源后显示页面；上传图片另需infra:file:create。

## 图片关联

分类和品牌保存前调用infra内部bind，携带当前原Bearer，只接固定服务地址，无重试/重定向/凭据日志。
infra核对真实tenant、product-image用途、上传者或同业务已有引用；引用后素材页禁止删除。
文件引用和删除锁同一元数据行；跨product/infra库没有分布式事务。
失败或超时可能遗留保守引用，更新/删除后的旧引用暂不自动释放，待权威业务核对清理；不能称全引用生命周期已完成。

## 用户自行构建/启动（本批未执行）

```powershell
# D:/aaa/a-yudao/mall
mvn -pl "qinghe-module-product/qinghe-module-product-server,qinghe-module-infra/qinghe-module-infra-server,qinghe-module-system/qinghe-module-system-server,qinghe-gateway" -am "-Dmaven.test.skip=true" package
java -jar .\qinghe-module-product\qinghe-module-product-server\target\qinghe-module-product-server-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

system/infra/Gateway新增RPC或路由也需重建重启；前端未typecheck/build，旧运行进程不代表这些源码已生效。

正式字段/差异/下一步见D:/aaa/a-yudao/mall-saas/docs/design/PRODUCT-CATALOG-REUSE.md。

## SPU/SKU规格校验前置（源码）

已择取SPU/SKU SaveReqVO、原SKU规格校验、组合键生成与当前租户SKU查询。
单规格一条，多规格按真正维度集合核对、所选值查真实归属；销售/展示属性分开，名称取数据库快照。
组合身份按property:value规范排序后SHA256，不受顺序/改名影响。max-skus默认200，可用PRODUCT_MAX_SKUS配置1~2000。
新SKU须初始库存，旧SKU须id/expectedVersion并禁止绝对库存输入；真正SPU归属/CAS/差集停用与库存流水仍待下一写入片段。
没有新增SKU HTTP，也没有发布/库存写入；配置/VO/校验存在不等于已经可保存商品。本批仅静态检查，未编译或运行。

## SKU差集和初始/手工库存Service（源码）

已依据原版新增/修改/移除SKU流程实现MANDATORY事务写入：稳定id和组合身份、父/子版本CAS、遗漏SKU停用并保留库存销量。
资料更新白名单SQL不含库存/销量，库存Service另用delta条件更新并保存不可变成功操作与before/after流水。
system当前Bearer数据范围RPC已接，按父SPU所属人/部门限制查询、修改及库存；不信任请求自报范围。
V005库存两表已创建并核对空表；初始库存0也有唯一初始流水。手工固定product:spu:update-stock权限，业务键+参数摘要去重。
Stock.adjust不占tenant配置锁，锁SPU与排序SKU，SPU可售库存从启用SKU汇总；手工库存不改变销量或资料版本。
同键同参返回原成功结果；无结果可能仍未提交，必须保持原业务键查询/重试。失败事务不落成功流水，不保存PENDING/失败结果。
本片段历史状态没有新HTTP/资源/前端或SPU外层保存；现已补外层SPU保存，见下段，仍无发布HTTP。
配送模板、商品完整校验、部门归属分配/转移与库存恢复UI、订单库存协议仍待后续；未编译或运行验证。

## SPU外层保存和真实配送校验（源码）

已依据yudao createSpu/updateSpu/getSpu写入外层事务。首次完整保存为下架、可信当前员工/部门归属，
分类品牌/销售展示规格/真实图片/模板验证 -> SPU -> SKU/库存流水 -> 统一父审计；失败回滚product库内写入。
更新先写新specType及资料白名单，再由SKU层推进父版本一次，外层只写一条父审计；不改owner/状态/库存/统计。
product新增trade-api依赖和原Bearer Feign模板验证，TRADE_BASE_URL默认http://127.0.0.1:8083；重量模板要求启用SKU有有效重量。
trade新库三表均空、无默认模板或费率，未配置合法模板时会明确拒绝保存。模板管理和具体地址核价后续继续实现。

当前没有商品保存/发布/回收的外部HTTP、商品管理前端或权限种子；没有真实保存运行验证。
后续还需发布完整校验、所属部门转移、分页投影、模板CRUD/核价与订单等；模板及infra引用校验不构成跨服务原子事务。
本批未运行Maven/编译/JUnit/guard/启动/HTTP，旧进程不包含新增源码。

## 商品管理HTTP/状态与库存结果恢复（源码，2026-10-28）

本节更新前述“无商品HTTP”的历史阶段。SPU路径/admin-api/product/spu：POST create、PUT update/update-status、DELETE delete(id/expectedVersion)、GET get-detail/page/get-count。
手工库存POST /admin-api/product/stock/adjust、GET operation(spuId/operationKey)，Gateway只登记9个对应方法路径，总145。
上架在锁内从真实启用SKU重验分类品牌/规格/金额/图片/配送/重量及汇总，允许零库存；状态CAS+审计，回收只能恢复为下架，删除保留历史SKU。
分页/计数SQL同一tenant/owner数据范围，详情SKU一致快照；后台白名单VO，Decimal计量返回字符串，无消费者成本投影。
原库存结果可通过专用已删父项owner小投影核对，不对外开放已删商品详情。

商家/admin/products真实列表/详情/上下架/回收和库存调整已接；库存原请求按realm/tenant/user保存sessionStorage，未知先查结果或保持原键重试，不能换号重复调整。
商品创建/修改API已接，完整编辑器尚未接；归属转移/导出/消费者与活动订单协议继续待办。
V020初始化210400~404五项资源和审计，不自动扩套餐/角色；分配后导航才显示。
未编译/测试/启动/HTTP/实际商品库存写入或浏览器验收；详情在mall-saas/docs/design/PRODUCT-SPU-MANAGEMENT-REUSE.md。


## 2026-10-29 订单库存内核（未开放写入 HTTP/RPC）

ProductOrderStockService提供deduct(request)、release(orderId,userId,reason)、get(orderId,userId)。输入由可信trade持久订单准备记录生成，不能绑定浏览器字段或把会员Token当库存写授权；后续保护RPC及订单/支付状态核查仍待接线。

条件扣减复用yudao原stock>=quantity的数据库保护，订单唯一结果行串行化重试，内容摘要绑定商品、数量、价格和资料版本；批量库存、明细及结果在同一本地事务提交。取消先到保存CLOSED，晚到扣减返回关闭状态；原扣减恢复一次，回补不依赖店铺经营状态/商品上架，库存溢出保留待核查而不假成功。先锁排序SPU父行，再锁SKU，与既有手工调整锁序兼容，不在本地事务内RPC。

状态0=CLOSED(取消先到，无扣减)，1=DEDUCTED，2=RELEASED；状态1只说明库存已扣，不代表订单已创建/支付成功。release只用于CREATE_FAILED或确定支付入口已关闭且未支付的UNPAID_CLOSED；未知支付不得释放。部分售后/可售退货需另按售后业务键和数量记流水，不能复用整单release；已确认付款销量尚待事件投影，未在预付款扣减时提前增加。

V008已创建两张空表，原库存和人工调整表不变，不重复执行。静态源码/SQL参数/实际模型列检查已做，未执行编译、测试、应用HTTP、真实库存写入或并发验证。证据：../../mall-saas/docs/evidence/yudao-order-stock-port.json。

## 2026-10-29 未付正式订单回补接线

新增精确服务POST /internal-api/product/order-stock/close-unpaid，沿用专用trade服务认证/租户传播，直接委托既有release(orderId,userId,UNPAID_CLOSED)，不接外部数量/自由原因、不经Gateway、不开放售后退款回补。可信trade须先持久取消并确认支付未付关闭。原订单已RELEASED重试只返回原明细，不再次加库存；DEDUCTED→RELEASED和SKU/汇总/明细由原本地事务完成。无新增产品表或业务库存写入，未编译/测试/启动/HTTP验证；本次接线证据见mall-saas/docs/evidence/yudao-cancel-resource-release-port.json。
