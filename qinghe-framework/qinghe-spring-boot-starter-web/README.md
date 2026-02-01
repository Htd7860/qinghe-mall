# Qinghe Web starter

Java 8 / Spring Boot 2.7.18。DEC-031：本批异常处理、自动装配及测试由助手按用户明确授权编写；其他后端仍由用户编写。2026-10-21用户已执行，助手只读报告确认Web39+4次及common现存8次均零失败/错误/跳过；助手没有执行编译或测试，证据位于正式工作区docs/evidence/web-exception-user-test-reports.json。

## 文件职责

- `framework/core/handler/GlobalExceptionHandler`：沿用用户已创建的包，负责MVC异常到CommonResult的转换。
- `framework/web/config/QingheWebExceptionProperties`：控制特定业务错误码的日志降噪，默认不忽略。
- `framework/web/config/QingheWebAutoConfiguration`：在Servlet应用中注册处理器，允许应用通过自己的GlobalExceptionHandler Bean替换。
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：Spring Boot 2.7的自动配置发现入口，无须扩大业务应用的包扫描范围。

依赖方向：业务服务 → 本starter → qinghe-common；本starter的Spring MVC/校验实现由Boot starter提供。Reactive Gateway不引入本Servlet starter。

## 保留与改造

保留yudao的ServiceException、CommonResult、业务错误码、成功码不能作为异常返回、统一异常处理及业务日志降噪语义。

采用Spring的ResponseEntityExceptionHandler作为扩展点，由Spring分派框架异常并提供HTTP状态/协议头，本模块统一响应体；不再自行维护一长串同等的instanceof判断。绑定错误同时考虑字段错误和对象级错误，空校验集合安全回退。异步/反射等已知包装异常最多解包16层，保持包装的业务异常语义；不任意遍历所有cause把其他错误改判为业务失败。

截图中的handleTableNotExists是上游多模块安装诊断，不进入本工程的公共HTTP响应：本模块不解析英文异常字符串、不硬编码bpm/pay/report等模块，不向浏览器返回表名/SQL/安装链接。数据库故障仍记录原始服务端异常，迁移与启动检查在对应服务的部署任务完成。

日志降噪按错误码配置，不与会变化的提示文字耦合。当前因此没有SetUtils前置依赖；无需为该处理器新增未使用的工具类，用户已有工具保持不动。

## 响应契约

| 情况 | HTTP状态 | CommonResult.code / msg |
|---|---:|---|
| 正常成功 | 由Controller决定 | 0及业务数据 |
| 普通业务拒绝，例如库存不足 | 200 | 保留非零业务码及受控业务提示 |
| 400～499中已定义的HTTP错误码 | 对应状态 | 保留业务码与提示 |
| 重复请求900 / 演示拒绝901 | 409 / 403 | 保留900 / 901 |
| 缺参、类型错误、JSON错误 | 400 | 400 / 请求参数不正确，不回显输入 |
| Bean Validation违反约束 | 400 | 400 / 约束定义的消息，不序列化被拒绝值 |
| 方法、媒体类型、响应格式错误 | 405 / 415 / 406 | 对应code及安全说明，保留Allow/Accept等头 |
| 文件超过大小限制 | 413 | 413 / 上传文件过大 |
| 未知异常、非法业务异常码、校验引擎配置失败 | 500 | 500 / 系统异常 |
| MVC异步超时 | 503 | 503 / 系统异常 |

所有错误响应设置JSON类型和Cache-Control: no-store；响应已提交时不重复写入。JSON code不会自动改变HTTP状态，本模块使用ResponseEntity显式设置状态。ResponseStatusException保留错误HTTP状态，但不返回其内部reason。

普通业务码不得与本表保留区间混用。ServiceException的null、非正数、500～599或UNKNOWN(999)均作为系统故障安全回退。401/403响应在这里有转换规则，不表示认证和权限校验已经实现。

## 配置与边界

下游Servlet业务应用引入此starter。若需要统一MVC未匹配路由的404响应，需要应用配置：

```yaml
spring:
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false
```

该配置适用于API应用；需要Spring静态资源映射的应用须单独设计资源路径，不能直接关闭。上述值已用于本模块的隔离测试应用，没有自动写入任何业务服务配置。

可选的降噪配置（不影响响应，仅影响指定业务拒绝的warn日志）：

```yaml
qinghe:
  web:
    exception:
      ignored-business-error-codes: []
```

不在日志中附加完整请求正文、查询串或校验拒绝值；系统异常堆栈仅留服务端。业务代码自身不能把密码/Token等秘密放进异常文本；约束消息也不应插入秘密字段的validatedValue。日志中的traceId读取服务端MDC，当前starter不自行生成trace或信任请求头，也不补造tenantId/actorId。

本模块覆盖Spring MVC异常。容器错误页、Filter/Spring Security链、Gateway、真实trace/租户上下文、infra错误日志持久化尚待对应工程接入；没有空的日志RPC或假成功。当前不等于整个项目生产验收通过。

## 用户执行验证

本批写有两类测试：GlobalExceptionHandlerHttpTest通过隔离的Boot MVC应用和真实自动装配发现执行请求；QingheWebAutoConfigurationTest检查发现元数据、Servlet条件和应用覆盖Bean。测试Controller仅在src/test，不进入生产jar。

在`D:/aaa/a-yudao/mall`执行：

```powershell
mvn -pl qinghe-framework/qinghe-spring-boot-starter-web -am test
```

这会同时编译依赖模块并运行common与web各自的测试。查看web模块的Tests run/Failures/Errors；仅BUILD SUCCESS而Tests run=0不能视为验证。Surefire3.2.5配置在本模块，避免旧默认插件漏跑JUnit5。

助手未执行上述命令；本批运行通过由用户执行和本地Surefire报告读取确认，不等于业务系统验收通过。common中此前删除的过量格式化参数用例仍是独立遗留项，本批没有更改该实现/测试。
