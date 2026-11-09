# Token Mapper MySQL 集成测试

新增离线验证批次：内部密码认证/登录/签发测试按DEC-056编写、由用户运行，单独命令和边界见[README-auth-issuance.md](README-auth-issuance.md)。该批不连接中间件，不表示下述MySQL测试已恢复执行或通过。

最新进度：2026-10-23用户已补两个Mapper的DO泛型、server测试依赖和Surefire，并明确暂不运行独立测试库验证、继续开发。本批保留未运行状态，不因此修改断言或记通过；后续联调前仍需执行。下方泛型/POM前置为初始指导，当前已静态确认存在。

2026-10-23，DEC-042：助手编写测试、用户执行。测试尚未编译/执行；不运行system应用，不连接Redis/Nacos，不访问qinghe_system。

## 先修正Mapper泛型

OAuth2AccessTokenMapper必须继承BaseMapperX<OAuth2AccessTokenDO>，OAuth2RefreshTokenMapper继承BaseMapperX<OAuth2RefreshTokenDO>，分别导入DO。原始BaseMapperX即使编译不报错，也不能正确绑定实体；测试初始化会检查元数据。

## 用户补system-server POM

dependencies加入（版本由既有BOM管理）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

在已有build/plugins中追加Surefire，保留原spring-boot-maven-plugin：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
</plugin>
```

## 用户创建独立库

本机qinghe_system_test当前不存在，助手没有执行建库。使用自己的数据库客户端在127.0.0.1:3306执行D:/aaa/a-yudao/mall-saas/implementation/database/bootstrap/003_create_system_test_database.sql；只创建测试schema，无账号或业务数据。不要在应用qinghe_system里运行此测试。

测试固定目标schema并检查实际catalog，要求两张Token表不存在后才创建；只清理/删除本次自己创建的两张表。同名表已存在时拒绝接管，不通过DROP已有表修复前置失败。测试resource的两个CREATE摘自本次V002，后续正式DDL变化须同步核对，不能默认一直等价。本地测试运行期间该schema不得由其他程序写入；并发DDL独占不由名称校验保证。

## 用户设置当前终端并运行

```powershell
$env:QINGHE_SYSTEM_TEST_ENABLED = "true"
$env:QINGHE_SYSTEM_TEST_URL = "jdbc:mysql://127.0.0.1:3306/qinghe_system_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:QINGHE_SYSTEM_TEST_USERNAME = "root"
$env:QINGHE_SYSTEM_TEST_PASSWORD = $env:QINGHE_DB_PASSWORD
```

最后一行只在当前终端已继承正确QINGHE_DB_PASSWORD时有效，不输出密码检查；新终端可读取用户级保存值到当前进程：

```powershell
$env:QINGHE_SYSTEM_TEST_PASSWORD = [Environment]::GetEnvironmentVariable("QINGHE_DB_PASSWORD", "User")
```

在D:/aaa/a-yudao/mall执行：

```powershell
mvn -pl qinghe-module-system/qinghe-module-system-server -am test
```

本测试类6个参数化方法覆盖两类Token，预期12次，失败/错误/跳过均0才通过。该命令还执行所有选中上游模块测试；tenant已有两项DEC-038写入反例可能导致整体失败，如需明确排除已知两项，可使用已标记标签：

```powershell
mvn -pl qinghe-module-system/qinghe-module-system-server -am test -DexcludedGroups=tenant-write-safety
```

排除是分批验证，不代表那两项通过或底层缺口已修。其他独立Redis/MySQL测试是否执行/跳过取决于其各自显式环境开关；每个模块分开记录，不把整体BUILD SUCCESS当所有集成场景均执行。测试结束后可移除本批启用变量：Remove-Item Env:QINGHE_SYSTEM_TEST_ENABLED。

## 覆盖与边界

- 两实体MyBatis真实Mapper注册、AUTO生成并回填id、独立session读回，避免一级缓存假象。
- JSON集合/Map与LocalDateTime微秒精度，revoked_time更新；通用查询仍能读撤销记录，真实认证服务必须另判有效资格。
- 逻辑删除后Mapper不可见、JDBC仍有deleted=1记录。
- 租户101不可读取/撤销更新/删除租户202记录；缺租户失败。
- 凭据大小写精确匹配与唯一索引实际拒绝重复。

用例显式赋审计字段，因为当前MetaObjectHandler未建立，本批不证明自动审计填充。没有伪造登录、Token签发/刷新/撤销缓存一致性、HTTP安全链或认证前租户引导验收；也不替代DEC-038保留的任意SQL归属修改反例。
