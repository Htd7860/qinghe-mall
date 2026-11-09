# 数据库迁移 SQL 草稿

2026-10-24当前员工表更新：用户新后端AdminUserDO为21字段，助手按DEC-046执行system/V006__create_system_users.sql并核对结构及0行；旧根V001为未执行的20列历史设计稿，已由当前V006替代，不再对qinghe_system执行V001。以system/README.md及docs/evidence/admin-user-table-created.json为当前落地证据。各目录手工迁移，不是已配置的统一Flyway扫描目录。

2026-10-23执行更新：用户按DEC-041明确授权后，system Token V002已由助手在本机qinghe_system落地，两表结构/索引/约束与空表检查完成，详情system/README.md和docs/evidence/token-tables-created.json。其他草稿及业务验收状态不由此改变。

2026-10-23新增：system/V002__create_oauth2_tokens.sql为两张Token表，映射当前用户新后端DO，**尚未执行/实测**；用户执行与目录边界见system/README.md。原秒杀V002验证不能替代本脚本验证，也不能把不同服务目录直接组合成同一Flyway迁移集。

作者：助手，按用户 DEC-017 授权编写。V001员工表保留原设计稿；新增三个服务独立目录的秒杀V002，共13张表。后端父工程、源码与测试仍由用户自己写。V002仅在一次性MySQL8.0.46空库验证，未向原库或正式库迁移，不是完整商城初始化包。

## V001 的设计依据及适配

- 字段和宽度参照固定版本的 AdminUserDO、BaseDO/TenantBaseDO 及 system_users 初始化 DDL；未复制上游账号数据、口令或自动增长当前值。
- DEC-019 确认用户名在租户内唯一，逻辑删除后保留。唯一键是 tenant_id＋username，不含 deleted；MyBatis 的逻辑删除过滤不会让唯一索引忽略删除行。
- 上游参考用户名索引不是这个联合唯一键，本稿是依据业务规则明确增加的适配，不能称为原样复刻。
- tenant_id 不提供默认 0，password 不提供默认空字符串，促使插入时明确提供租户与已编码密码；必填字段不等于已验证身份/密码内容，相关应用校验仍由用户实现。
- 账号状态 0 开启、1 关闭，deleted 使用原 bit(1) 语义；恢复原账号时数据库默认值不自动生效，Service 必须按恢复规则设置状态、清理旧授权/会话，不直接改一个删除标志就认为完成。
- 保留 utf8mb4_unicode_ci，与参考列排序规则一致；账号比较通常不区分大小写且受此排序规则的字符等价性影响，大小写/空格及 Unicode 边界需在目标数据库与输入校验中核对。
- Secondary 索引加租户前缀，按状态列表、部门数据范围、手机号/邮箱定位的已有使用场景设计；最终保留与顺序要结合实际查询和执行计划复核。手机号、邮箱此处不是唯一标识，不擅自添加其唯一约束。
- 不创建物理外键、级联、数据库、USE 或 DROP；部门和岗位所属租户的完整性由对应服务校验。保留 yudao create_time/update_time 命名及公共 DO 映射，未声称逐条照搬阿里手册的全部字段约定。

## 执行前及后续验证

目标 MySQL 版本、隔离 schema/账号、字符排序与迁移执行器需明确后再运行。SQL 没有 IF NOT EXISTS：同名表已经存在应失败并核对版本，不能悄悄把旧结构当新版本成功。

数据库验证至少包括：同租户重名失败、跨租户同名成功；删除后同名新建仍失败；原 id 恢复后历史身份保持；必填租户和密码缺失失败。实际恢复、权限/会话失效和跨租户访问需要应用集成测试，单张表和唯一索引不提供这些保证。

当前未执行上述数据库验证。已逐列核对 20 个字段名/类型、单条 CREATE、无数据变更/选库操作及关键索引和默认值；本机未发现 SQL 解析器，未做完整语法解析或 MySQL 执行，不能声称建表通过。静态记录在根项目 `docs/evidence/system-users-ddl-static.json`。其他 System 表与域名/部门等设计继续评审，本文件不是完整数据库初始化包。


## 秒杀 V002 / DEC-027

- 字段字典为SQL每列COMMENT，逻辑ER、状态、事务与恢复见 [SECKILL-DATA](../../../docs/design/SECKILL-DATA.md)。
- `promotion/V002__create_seckill.sql`：8表，活动、SKU池、发布互斥、个人限额、持久申请、额度流水、Outbox、Inbox。
- `product/V002__create_seckill.sql`：2表，实物库存划拨及幂等流水。
- `trade/V002__create_seckill.sql`：3表，申请唯一建单/关闭墓碑、Outbox、Inbox。
- 各服务独立migration历史，不能将同名V002递归作为同一个Flyway目录。无外键、无账号数据、不选择库、不删除表。MySQL8.0.16+保证CHECK生效；迁移前备份/确认库归属，DDL隐式提交，失败后逐表核查前向修复。
- 根目录运行 `python scripts/validate_seckill_ddl.py` 可复现：本地mysql:8.0镜像，network=none、无暴露端口、无主机卷、临时内存数据目录；完成后删除准确的自建容器。
- 13表建表和18项结构/约束检查通过，见 `docs/evidence/seckill-ddl-validation.json`。唯一绑定并不能自动禁止复活UPDATE，仍需后端状态/版本/事务。Redis/MQ/XXL、并发恢复未验收。
- 前文“未执行”仅指原V001员工表；V001内容没改，没有因为本次验证就记完整商城或原库通过。
