# qinghe-gateway

2026-10-27角色授权选项增量：新增system-role-menu-read一条GET路由及四个authenticated规则，匹配/admin-api和/platform-api下/system/menu/simple-list、/system/permission/list-role-menus。system独立核对端/可信tenant与system:permission:assign-role-menu，按套餐及启用父链裁剪；只是选项/已选ID读取，没有授权保存或初始化。其余denyAll；尚未编译/运行或接前端，见mall-saas/docs/design/RBAC-ROLE-MENU-READ-HTTP.md。

2026-10-27角色读取增量：新增system-role-read一条保留路径GET路由，仅匹配商家/admin-api/system/role/page、/get及平台/platform-api同后缀四个入口；安全链要求authenticated，system再次认证/核对端与可信租户，并通过@PreAuthorize检查system:role:query。平台入口只查tenant0，不能请求任意商家。其他业务仍denyAll，无初始化授权/匿名角色列表/写接口。本条覆盖下文早期“只有两条路由/方法权限尚未实现”的历史描述；尚未编译、运行或接前端角色页面，见mall-saas/docs/design/RBAC-ROLE-READ-HTTP.md。

2026-10-27集中修复（DEC-061）：安全链已显式使用NoOpServerRequestCache，不再保留默认WebSession请求缓存。登录组合保护采用来源总请求量＋system租户账号失败量两层，无额外三元组桶；验证码/审计及真实运行验收仍待完成。当前集中清单见mall-saas/docs/design/FOUNDATION-GAP-FIXES.md。

2026-10-27 / TASK-012 / DEC-057、058、060：独立WebFlux网关、每次远程校验的Bearer过滤器、商家/平台密码登录POST与身份查询GET精确路由、登录来源请求限额和统一错误响应已编写，尚未编译运行。身份响应已接数据库角色/资源/套餐范围聚合，无授权则为空；业务后端方法级鉴权、刷新退出HTTP和B12完整保护仍未完成，不能称浏览器登录或公开部署已就绪。

账号与来源保护：system两密码登录已编写账号维度固定900秒5次失败/在途准入，Gateway在认证之前按直接连接IP限制两登录POST合计60秒60次请求；均有Redis故障有界本地保护，尚未编译运行。Gateway不信任外来Forwarded/X-Forwarded-For，不读取密码请求体；共享代理IP/可信反向代理部署需另行配置。验证码/审计和B12整体验收继续待办。详见mall-saas/docs/design/LOGIN-FAILURE-LIMIT.md、GATEWAY-LOGIN-SOURCE-LIMIT.md。当前代理15秒超时不取消system已在执行的签发，超时仍不能自动重试登录。

依赖Spring Cloud Gateway、Spring Security、Actuator、Boot Reactive Redis/Validation和qinghe-common（共用CommonResult/Token检查DTO），版本继承既有BOM（Boot2.7.18、Cloud2021.0.9/Gateway3.1.9）。不引Servlet Web/Security starter，不扫描system服务、不直接接业务数据库/Nacos。ReactiveStringRedisTemplate只用于来源限额，默认本机6379/DB1及独立Key前缀，环境配置见application.yaml。过滤器使用WebClient请求配置的system固定路径，不建设Cookie会话换Token层。

默认绑定127.0.0.1:8088，GATEWAY_BIND_ADDRESS/GATEWAY_PORT可覆盖。未启用发现自动路由；两条精确路由分别匹配后台POST auth/login与GET auth/session，均保留路径转发system。登录POST与GET health公开，商家/platform两个auth/session GET要求已认证，其他路径仍拒绝。关闭form/basic/默认logout，不加载Boot默认账号，不通过默认WebSession恢复SecurityContext；在此不接受Cookie自动认证的边界显式禁CSRF。当前已接Bearer远程验证、三端/租户绑定与头清洗，尚待运行验证；不能直接打开泛化转发。

当前Bearer过滤器只在Security AUTHENTICATION位置注册一次，清理外部login-user等身份声明和Cookie；严格读取单一Authorization，不从URL取Token；带凭据时每次调用`GET {system-base-url}/internal-api/auth/token/check`，只转发Bearer。system该接口不放行匿名，复用现有Servlet安全链真实校验后返回当前身份。网关核对返回结构、过期、三端用户类型/租户0与正数边界、显式tenant-id一致性；用ReactiveSecurityContextHolder建立当前请求身份，并设置可信tenant-id，原Bearer保留供未来业务服务再次独立校验。无本地登录缓存，未实现RBAC权限不伪造grant。系统不可用/超时/非预期响应503，凭据无效401，入口或租户不符403，错误均CommonResult且no-store；不把下游业务异常改成认证失败。

`qinghe.gateway.system-base-url`默认http://127.0.0.1:8080，可由SYSTEM_BASE_URL配置为可信内部服务；不能指回Gateway自身或公开不可信地址。手写WebClient认证查询固定3秒超时、不跟随重定向、响应上限64KiB，无自动重试。Gateway业务代理另配2秒连接/15秒响应超时，不配置Retry。双方LocalDateTime沿用已有内部DTO约定，运行时区/时钟需一致；跨实例与五秒目标仍待真实验证。内部check响应不含access/refreshToken；两个登录POST响应是含凭据的TokenPair，no-store，不返回Identity/DO。详见mall-saas/docs/design/ADMIN-PASSWORD-LOGIN-HTTP.md；B12完整保护尚未验收，本机开发接线不等于公开部署就绪。

## 用户验证

身份GET当前返回前端Identity同形VO：realm/userId/name/tenantId/tenantCode/role/permissions/expiresAt；ID来自已认证上下文，商家编码来自真实租户，昵称来自当前账号，no-store，不返回凭据。role是实际启用角色名称的展示字符串，permissions来自真实启用角色资源/套餐范围聚合，无绑定为空且无超级管理员兜底；商家store只比对不选择租户，平台tenantId为字符串0且tenantCode为空。详见mall-saas/docs/design/ADMIN-CURRENT-IDENTITY.md与RBAC-PERMISSION-AGGREGATION.md。此URL不创建HttpSession，消费者接口仍未开放。

助手未执行以下命令，模块尚未编译/启动验收。在D:/aaa/a-yudao/mall执行：

```powershell
mvn -pl qinghe-module-system/qinghe-module-system-server,qinghe-gateway -am package -DskipTests
java -jar .\qinghe-gateway\target\qinghe-gateway-1.0-SNAPSHOT.jar
```

新终端检查：

```powershell
curl.exe -i http://127.0.0.1:8088/actuator/health
curl.exe -i http://127.0.0.1:8088/admin-api/auth/session
```

需先用既有local启动方式重新启动system以加载新增接口，再启动Gateway。依赖正常时预期health200且仅汇总健康状态；新增Redis健康指标后Redis故障可显示DOWN，来源限额本地降级与依赖健康分别解释。两个登录POST可以匿名转发，仍需真实账号密码/租户及客户端资格；两个身份GET要求有效后台Bearer并核对端和租户；其余路径被安全链拒绝（401或403）。没有账号初始化，先验证缺字段/JSON错误400、未知账号401等拒绝分支。安全链拒绝、Bearer/来源限额及网关未处理异常共用CommonResult写出：no-store、401挑战、429保留Retry-After，连接失败502、超时504、未知500固定提示；已提交不改写、下游正常HTTP结果继续透传，详见mall-saas/docs/design/GATEWAY-ERROR-RESPONSES.md。全部预期尚未实测，打包成功或健康检查不证明完整登录、刷新/退出、身份头清洗及多实例验收通过。本模块未新增测试，DEC-059不扩大为Gateway测试代写授权。

参考D:/aaa/a-yudao/mall-saas/backend/yudao-gateway/pom.xml、GatewayServerApplication.java及application.yaml。保留独立网关/入口职责；本项目按DEC-060改用与原版接近的浏览器Bearer，前端参考原版axios请求拦截器，仍按既有每次内部校验和五秒要求实现，不复制原版较长资格缓存窗口。本片段安全链为本项目新增，未声称存在yudao同名方法。
