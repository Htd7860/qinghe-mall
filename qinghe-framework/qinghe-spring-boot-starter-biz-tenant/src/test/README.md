# 租户测试运行说明

本批由助手按 DEC-036 编写，用户运行。助手未运行 Maven、编译或测试，生产 Java/POM 未改。

## Web 上下文测试

2026-10-22验证更新：助手已只读核对用户生成的三份Web Surefire报告，21+5+2共28次全部通过，失败/错误/跳过0，源码哈希匹配编写快照；执行者用户。MySQL缺密码变量截图与这三份Web报告分开，不代表两项直接Mapper反例解决。

用户已补齐QingheTenantWebAutoConfiguration的return registration；当前在既有租户测试批次中补齐三类Web验证，不改生产Java/POM。

- TenantContextWebFilterTest：6个方法，参数化展开21次，含缺省/空白/非法/溢出编号、同线程连续请求、旧ignore清理、下游RuntimeException/ServletException/IOException原样传播及finally清理。
- QingheTenantWebAutoConfigurationTest：5次，imports、默认/显式开关、Servlet/非Web条件、注册类型、URL和顺序；按类型查找，不限制用户@Bean方法名。
- TenantContextWebFilterHttpTest：2次，真实回环随机端口Tomcat，检查容器实际注册一次、候选租户上下文可见及真实HTTP400。启动后自动关闭，只导入测试所需Web配置，不加载system/Nacos/数据源；测试Controller仅位于src/test，不进入生产JAR。

本批28次用户执行通过，与此前31项通过记录分批保存。当前仅验证普通REQUEST分发，不证明可信身份、RBAC或异步/错误重分发生命周期。

在mall根目录仅运行本批，避免混入DEC-038已记录的两个直接Mapper写入反例：

```powershell
mvn -pl qinghe-framework/qinghe-spring-boot-starter-biz-tenant -am test "-Dtest=TenantContextWebFilterTest,TenantContextWebFilterHttpTest,QingheTenantWebAutoConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

最后一个参数允许参与-am构建的上游模块没有这三个指定测试类，不因无匹配测试而报错；tenant模块自身应实际执行28次，不是0次。原测试与失败断言未改，也没有在项目配置里自动排除它们。

## 不连接数据库的测试

在 D:/aaa/a-yudao/mall 执行：

```powershell
mvn -pl qinghe-framework/qinghe-spring-boot-starter-biz-tenant -am test
```

没有设置 QINGHE_TENANT_TEST_URL 时：

- TenantContextHolderTest：8次，原批次已由用户验证通过。
- TenantDatabaseInterceptorTest：11次，真实MP元数据，默认过滤、显式例外、优先级、缺失租户和快照规则。
- QingheTenantAutoConfigurationTest：4次，imports登记、真实Spring条件装配、属性绑定、同一插件链中的租户先于分页。
- TenantDatabaseMySqlTest：10次跳过；跳过不是通过。不依赖Spring Boot整应用或Nacos启动。

2026-10-22用户执行结果已核对：Holder8、规则11、自动配置4共23次通过，失败/错误为0；MySQL10次跳过。执行者为用户，助手只读报告，没有复跑。

## 真实 MySQL 集成测试

测试只接受 jdbc:mysql://127.0.0.1:3306/qinghe_tenant_test 或 localhost 同地址独立库；实际连接catalog也必须相同。
2026-10-22助手已按用户DEC-037明确授权执行正式工作区 implementation/database/bootstrap/002_create_tenant_test_database.sql，核对该库字符集utf8mb4、表数0。无需重复建库；JDBC测试连接仍需下面的环境变量，尚未验证。

```powershell
$env:QINGHE_TENANT_TEST_URL = "jdbc:mysql://127.0.0.1:3306/qinghe_tenant_test?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:QINGHE_TENANT_TEST_USERNAME = $env:QINGHE_DB_USERNAME
$env:QINGHE_TENANT_TEST_PASSWORD = $env:QINGHE_DB_PASSWORD
mvn -pl qinghe-framework/qinghe-spring-boot-starter-biz-tenant -am test
```

后两个变量要有值；IDE运行时也须单独配置环境变量。不要将真实密码写入源码或提交到Git。

测试会在独立库中新建 tenant_isolation_test_record 和 tenant_isolation_test_platform 两张表；若已存在则拒绝接管。每项测试重置本次创建的表中虚构数据，结束后仅删除本次成功创建的测试表，不创建/删除数据库，不连接 qinghe_system。

覆盖跨租户ID查询、列表/count、真实分页COUNT、更新、逻辑删除、缺失上下文、平台例外、插入自动带租户，以及两项写入归属反例。这里手动建立真实MP链验证SQL执行；Spring自动注册由另一测试类单独验证，不冒充已跑通system服务完整接入。

## 当前应暴露的写入保护缺口

DEC-038：用户决定当前不实现额外SQL层写入归属防护，采用后端VO/Service可信写入边界；下面两项直接Mapper反例作为能力边界保留，不作为当前搭建阻塞，不改断言/自动禁用。选择该边界不能据此声称任意Mapper写入受保护。真实业务入口后续按归属赋值和更新白名单验收。下方排除标签命令可用于当前分批执行，记录中仍保留这两项未满足的更强底层保证。

2026-10-22用户实际执行已核对：23项非数据库检查通过；10项MySQL检查中8通过、下面2项失败，合计31通过/2失败/0错误/0跳过。尚未实现写入保护，未将这两项标为通过。它们直接调用Mapper，要求强于原生行过滤插件的承诺，不能由此推断尚未实现的HTTP/服务入口已可被攻击；后续可信写入边界须在实际入口验证。

两项 @Tag("tenant-write-safety") 测试坚持业务安全目标：

1. 租户101显式插入tenant_id=202，必须拒绝或改为可信租户101。
2. 租户101更新自己的记录时，不得把tenant_id改为202。

当前生产实现只有WHERE过滤，尚无完整写入保护，按静态源码预计这两项会失败。不能修改断言使越权写入算通过；也不能直接断言所有RuntimeException代表安全成功，因此拒绝后仍独立查询数据库检查结果。
后续须在正式确定的可信写入边界实施保护；若设计由服务层负责，应将相应安全验收移到真实服务入口并保留底层能力边界，不能据此声称原生Mapper任意SQL都安全。

若本次先验证已有查询/分页机制，可以显式暂不运行这两项：

```powershell
mvn -pl qinghe-framework/qinghe-spring-boot-starter-biz-tenant -am test "-DexcludedGroups=tenant-write-safety"
```

这只是分批执行，必须记录两项尚未通过；全量验收仍需执行全部测试。未配置独立库时10项跳过，不能当31项或33项通过。

## 仍未覆盖的业务验收

这批没有实现或验收真实登录/RBAC、HTTP头伪造、服务间身份传播、TTL线程池装饰、复杂JOIN/子查询/批量写入/INSERT SELECT/ON DUPLICATE KEY。后续随对应实现补测试，不能用本批替代这些验收。
