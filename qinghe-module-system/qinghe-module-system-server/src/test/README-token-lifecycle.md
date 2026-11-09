# 刷新与退出离线测试

用户执行更新（2026-10-26）：只读核对四份Surefire报告15+28+51+7=101次，失败/错误/跳过均0；相关三测试Java与七生产Java哈希匹配编写快照，证据mall-saas/docs/evidence/token-lifecycle-user-test-reports.json。下文“尚未编译/执行”是编写时历史，本条覆盖；真实数据库/缓存及Gateway验收边界不变，助手未复跑。

2026-10-26，DEC-059：用户明确“你写测试，我运行”。本批助手只写测试和夹具说明，不改生产Java/POM/运行配置，不执行编译/Maven/JUnit/guard/启动或外部服务操作。

## 代码与预期数量

| 测试类 | 预期次数 | 范围 |
|---|---:|---|
| OAuth2TokenLifecycleTest | 51 | 33个普通测试，InvalidParent参数8、BadInput参数6、ParentMismatch参数4 |
| AdminAuthServiceImplTest | 15 | 原11个登录用例保留，新增4个刷新/退出委托与故障传递用例 |
| AdminTokenIssueServiceImplTest | 28 | 原签发回归，夹具仅将Redis DAO mock改为可共享字段 |
| AdminUserServiceImplTest | 7 | 原账号与密码回归 |
| 合计 | 101 | 新增55次，预期数不是实际通过数 |

新增生命周期测试复用AdminTokenIssueServiceImplTest.Fixture及小型事务配置。使用真实Spring代理/DataSourceTransactionManager，JDBC DataSource/Connection、Mapper、Redis DAO及资格Service均为Mockito对象；无SpringBootTest/应用扫描、真实数据库连接、Redis客户端或Nacos客户端，不需要密码、环境变量或Docker。TableInfoHelper仅初始化Lambda列名元数据，不创建表、不执行SQL。

## 用户运行

在D:/aaa/a-yudao/mall执行：

```powershell
mvn -pl qinghe-module-system/qinghe-module-system-server -am test "-Dtest=AdminUserServiceImplTest,AdminAuthServiceImplTest,AdminTokenIssueServiceImplTest,OAuth2TokenLifecycleTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

预期system-server模块101次，Failures/Errors/Skipped均0。必须核对该模块的Surefire结果，不能把父工程成功、未找到测试或跳过当作通过。报告目录为qinghe-module-system/qinghe-module-system-server/target/surefire-reports。命令会编译当前测试源码，但只选择上述四类运行；不执行既有Token MySQL/真实Redis/租户写入反例。

本批尚未编译或执行，没有通过报告。旧46次离线通过是刷新/撤销变更前历史。

## 覆盖什么

- 刷新：旧refresh和绝对到期不变、新随机access/可信归属/scopes/审计和有效期上限，平台与商家分支，父→资格→子调用顺序；不存在/过期/撤销/跨client/非ADMIN/畸形输入与关联不一致；资格401及基础设施故障；等待阶段到期、批量数量不符、插入异常/0行、提交异常；提交成功才删全部旧缓存，缓存故障不触发DB rollback；新事务独立于外层回滚，直接或只读调用拒绝。
- 撤销：父→子调用顺序、批量固定更新字段、重复撤销保留首次时间并仍清缓存、未知access只清精确Key、过期会话仍可退出；跨client/缺父/身份关联不符/缺目标不写；父更新数量错误、子写失败/数量错误、上下文恢复；提交异常不清缓存、提交后缓存失败尝试其他Key并允许幂等重试；无事务/只读/畸形输入拒绝。
- 认证入口：刷新/退出直接委托对应服务，不重新密码认证或查账号资格；凭据错误和提交后缓存故障原样传播。

## 不能证明什么

Mapper只返回模拟值，不运行租户插件/JSQLParser/JSON处理器、MySQL约束/自增或真实SQL。SQL Wrapper断言只检查固定查询/更新构造；commit/rollback验证只证明模拟连接的调用顺序，不能证明两表数据真的原子回滚。重试测试显式设置模拟的已撤销快照，不冒充真实数据落库。

没有真实并发数据库锁等待、死锁、提交结果未知恢复、缓存延迟回填/TTL/时钟或多实例五秒效果。到期分支通过调整内存夹具时间确定性触发，不睡眠或声称模拟了真实锁等待。资格依赖为mock，不证明真实客户端/租户全部规则。Gateway Cookie/CSRF、公开HTTP、refresh重放检测、密码变更全会话失效及日志仍未验收。Token MySQL继续暂缓，不创建库/账号。

参考yudao-module-system-server/src/test/java/cn/iocoder/yudao/module/system/service/oauth2/OAuth2TokenServiceImplTest.java的testRefreshAccessToken_success/removeAccessToken相关测试；原版继承BaseDbAndRedisUnitTest，本批按已确定的离线分工实现，不照搬其数据库/缓存夹具。
