# 内部账号密码认证与Token签发测试

生命周期测试更新（2026-10-26，DEC-059）：新增OAuth2TokenLifecycleTest预期51次，当前AdminAuthServiceImplTest增加4次达到15次；签发夹具共享Redis DAO mock，原签发28次与账号7次不变。最新四类回归预期101次，尚未编译/执行，命令与边界见同目录README-token-lifecycle.md。下文46次仅为历史批次记录，不能替代本轮报告。

刷新实现更新（2026-10-26）：AdminTokenIssueServiceImpl新增Redis DAO构造依赖，现有签发测试夹具仅追加mock；原用例与断言不变，未新增刷新/撤销测试。生产保存access的私有方法被登录与刷新共用，旧46次报告不能证明当前变更通过，须用户重新运行原回归并另安排新行为验证。

退出接线更新（2026-10-25）：AdminAuthServiceImpl新增撤销服务构造依赖，现有登录测试夹具仅补充对应mock，测试用例及断言不变，未新增退出测试。下述46次通过为接线前历史报告，本次源码变更尚未由用户重新编译/运行，不代表撤销或退出已验收。

用户执行更新（2026-10-25）：已只读核对三份Surefire报告7+11+28=46次，失败/错误/跳过均0，源码快照一致；正式证据mall-saas/docs/evidence/admin-auth-issuance-user-test-reports.json。下文尚未执行为编写时历史，现由本条覆盖；真实MySQL和完整系统验收边界不变。

2026-10-25，DEC-056：用户明确“你写测试，我运行”。助手编写本批三个测试类及说明，尚未编译或执行；未修改生产Java/POM/配置，未启动system或连接MySQL/Redis/Nacos。

## 本批范围

| 类 | 预计执行次数 | 内容 |
| 类 | 预计执行次数 | 内容 |
|---|---:|---|
| AdminUserServiceImplTest | 7 | 实际低成本BCrypt匹配、UTF-8 72字节边界、账号资格和故障分类 |
| AdminAuthServiceImplTest | 11 | 平台/商家登录顺序、失败不签发、改密、统一401及基础设施故障保留 |
| AdminTokenIssueServiceImplTest | 28 | 14个普通方法、4种缺行、10种非法输入；签发数据、上下文恢复、查询构造、事务代理和JDBC提交/回滚调用 |
| 合计 | 46 | 预计值，只有用户运行报告可以确认实际结果 |

测试仅注册小型Spring上下文、真实DataSourceTransactionManager及生产签发Bean；DataSource/Connection和全部Mapper均是Mockito模拟对象，没有真实连接地址，不调用DriverManager/SqlSessionFactory，也没有SpringBootTest/系统自动扫描。

密码测试使用BCrypt成本4加速，这只在测试对象中设置，生产编码器不变。资格Service在登录/签发编排测试中为mock，账号实际资格分支另在AdminUserServiceImplTest验证；本批不冒称已覆盖真实租户/套餐/客户端的全部业务分支。

## 用户执行命令

在D:/aaa/a-yudao/mall运行：

```powershell
mvn -pl qinghe-module-system/qinghe-module-system-server -am test "-Dtest=AdminUserServiceImplTest,AdminAuthServiceImplTest,AdminTokenIssueServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

`-am`编译所需上游模块，`-Dtest`只选择这三个测试类；`surefire.failIfNoSpecifiedTests=false`允许上游模块没有这三个类，避免误报找不到指定测试。查看system-server模块自己的Surefire结果，不能只凭父工程BUILD SUCCESS或某上游模块0次执行当作本批通过。不要另加-DskipTests，否则只是跳过测试。

命令不运行既有OAuth2TokenMapperMySqlTest、真实Redis测试或已知租户写入反例。Maven仍会编译当前模块的测试源码；若编译报错，请保留完整错误，不能把未运行记作通过。

预期本批46次，Failures/Errors/Skipped均0；这是预期，不是已取得的结果。报告位置：qinghe-module-system/qinghe-module-system-server/target/surefire-reports/。

## 事务测试的实际边界

- 使用真实Spring事务代理和DataSourceTransactionManager，检查READ_COMMITTED、事务超时配置、独立连接提交/外层回滚、异常时rollback调用，以及commit异常不会向调用方返回令牌对。
- 数据库连接是mock，验证rollback()被调用不等于证明真实MySQL第一条INSERT已撤回；没有真实数据可回滚。
- FOR SHARE断言检查MP查询条件构造，没有执行租户插件/JSQLParser解析或MySQL锁定读取。
- 不验证JSON/JDBC映射、自增回填的数据库行为、真实锁竞争/等待/死锁、数据库提交结果未知后的恢复、缓存回源、多实例撤销窗口、公开登录/限额/日志或Gateway Cookie+CSRF。
- 既有Token MySQL测试继续按用户决定暂缓，不创建测试库或插入种子账号。上述真实集成范围仍须后续单独执行。

测试中的密码、hash和故障字符串都是独立虚构夹具，没有读取或记录本机真实凭据。生成的随机令牌只存在于本次测试内存，不写入Redis/MySQL。
