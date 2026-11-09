# Redis 序列化与自动配置测试

2026-10-23 / DEC-040：本批由助手编写，用户运行。生产 Java、POM 和运行配置未由助手修改；测试尚未编译或执行，不把存在测试文件当作通过。

验证更新：2026-10-23用户已执行，助手只读核对两份Surefire XML，14+4=18次，失败/错误/跳过均0，源码与编写快照匹配。正式记录见D:/aaa/a-yudao/mall-saas/docs/evidence/redis-user-test-reports.json。上段及下方前置/预计数保留为初始编写说明；当前测试依赖与插件已由用户补齐，助手未复跑。真实Redis读写和TTL仍未验证。

## 先由用户补本模块 POM

Redis 模块当前没有测试依赖和 Surefire，兄弟模块的配置不会传过来。在 qinghe-spring-boot-starter-redis/pom.xml 的 dependencies 增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

在 project 下增加 build/plugins（不是写进 dependencies）：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
        </plugin>
    </plugins>
</build>
```

starter-test 提供 JUnit 5、参数化测试、AssertJ、Mockito 和 Spring Boot 测试工具，版本沿用 BOM。不需要 Lombok、真实 Redis、MySQL、Nacos或密码环境变量。

## 用户运行

在 D:/aaa/a-yudao/mall 执行：

```powershell
mvn -pl qinghe-framework/qinghe-spring-boot-starter-redis -am test
```

只构建该模块及 Maven 上游依赖，不是全工程测试；common 的既有测试也可能执行。不要加 skipTests。用户提供结果后再核对本模块 target/surefire-reports 两份 XML 中 tests/failures/errors/skipped，源码有改动则需匹配当前版本。

## 覆盖什么

| 测试类 | 行为 |
|---|---|
| RedisSerializationTest | 普通/final模型及根LocalDateTime恢复；纳秒精度；字符串/布尔/整数/大Long/BigDecimal/BigInteger值与类型；列表模型与顺序；Map嵌套时间、金额及集合；null/空字节；损坏JSON拒绝；范围外java.io.File类型拒绝 |
| QingheRedisAutoConfigurationTest | imports元数据；默认模板的连接工厂及四处serializer；应用同名模板退让；真实Redisson V2和Boot自动配置处理顺序与默认模板退让；StringRedisTemplate保留字符串serializer |

预计前者参数展开14次、后者4次，共18次。实际次数以运行报告为准，预期数不是通过记录。

读取顺序建议：先看 modelShouldRetainItsTypeAndNanosecondExpiry，理解准备对象→写成字节→用另一个serializer读回→校验字段；再看 applicationTemplateShouldReplaceTheDefaultWithoutBeanOverriding，理解测试配置如何模拟业务应用自定义Bean。

## 边界

本批纯字节往返和隔离Spring上下文，客户端/连接工厂用Mockito替身，没有实际Redis读写，也不启动system。模型只在src/test存在，不是业务实体或表结构。File用例仅构造JSON，不访问文件。

不证明实际Redis数据库编号、网络读写、TTL/Hash命令、租户key隔离、缓存NullValue、所有Java数组/集合类型、Token签发撤销或生产验收；这些随对应实现继续验证。不得为了绿灯扩大类型放行范围或删除金额/类型恢复断言。

## 下一批：真实 Redis 集成验证（尚未运行）

验证更新：用户已执行本批，助手只读核对报告与源码哈希，原18项及新5项合计23次零失败/错误/跳过。详见D:/aaa/a-yudao/mall-saas/docs/evidence/redis-integration-user-test-reports.json。本节标题及后续说明保留编写时状态，当前以本条最新记录为准。

RedisDatabaseIntegrationTest 在同一 Redis 测试批次下由助手编写、用户执行，包含5项真实行为：对象/纳秒时间往返及可读JSON、Hash字段和值、服务器TTL实际过期、精确删除不影响另一个Key、StringRedisTemplate保留纯文本。原18次离线测试保留不改。

固定连接本机127.0.0.1:6379、无密码的既有容器，固定DB15。应用开发约定使用DB1、既有参考数据在DB0，本测试不写这两个DB。启动前从服务器CLIENT LIST核对本次命名连接的db=15，再确认DBSIZE=0；DB15非空会拒绝测试，不自动清理旧数据。该空库检查不是跨程序独占锁，本地运行时仍需把DB15留给测试。

每次随机Key前缀qinghe:test:redis:<UUID>:；只删除本次记录的完整Key，不执行FLUSHDB/FLUSHALL/通配符清理。普通值有30秒兜底TTL，Hash在HSET后设置TTL，正常/断言失败后均尝试精确清理，最后关闭客户端。若进程被强制结束，遗留Key须先检查归属；不通过清空DB解决测试前置失败。

不需要增加POM依赖，也不需要Nacos、MySQL、数据库密码或system进程。Docker Redis必须运行（本次只读检查容器正常、DB15为空，助手未执行测试）。模板调用用户的配置方法并显式初始化，实际自动配置顺序已由原4次隔离配置测试覆盖。

在 D:/aaa/a-yudao/mall 的当前PowerShell中执行：

```powershell
$env:QINGHE_REDIS_TEST_ENABLED = "true"
mvn -pl qinghe-framework/qinghe-spring-boot-starter-redis -am test
```

Redis模块预期18+5=23次，失败/错误/跳过均0才算本次通过；上游common的次数另计。没设置标志时，新5项会明确跳过，不能把“18通过、5跳过”记为真实Redis验证通过。服务器过期用例约等待5秒，按有上限的轮询判断，不要求人工等待或手动删除Key。

运行完可在同一终端撤销本次启用标志：

```powershell
Remove-Item Env:QINGHE_REDIS_TEST_ENABLED
```

通过仍不证明system正在使用DB1（该类使用独立测试连接）、项目缓存租户隔离、Token服务、崩溃后的缓存一致性或Spring Cache功能。本次未运行，也不覆盖已有18次通过记录。
