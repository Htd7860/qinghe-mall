# system 手工迁移说明

## V010/V011：共同管理事务与成功变更审计（2026-10-27，已执行）

DEC-062完整模块实现的前置。V010在确认同名表/新列/约束不存在后创建system_management_mutex并初始化唯一id=1配置行，两个平台管理员引用仍NULL；给system_tenant追加可空admin_role_id及CHECK，未赋予用户/角色权限，旧tenant仍0行。V011创建system_management_audit（11列/3索引/6启用CHECK、0行）。目标均本机127.0.0.1:3306/qinghe_system、MySQL8.0.43/QIANG，退出0无警告；无应用业务初始化或覆盖，不能重跑。详见design/SYSTEM-MANAGEMENT-COMPLETION.md及后续证据。用户已明确本批不测试，DDL元数据核对不作为Java事务/权限测试通过。

## V009：角色菜单成功变更审计（2026-10-27，已执行）

按DEC-046在本机127.0.0.1:3306/qinghe_system、MySQL8.0.43/QIANG预检同名对象/约束不存在后，执行[V009__create_role_menu_audit.sql](V009__create_role_menu_audit.sql)单个CREATE，退出0无警告；核对10列、3索引、7项启用CHECK、InnoDB/无外键/0行，旧role/role_menu各0行保持，未执行授权DML/初始化/修改账号权限。本库不要重复执行。包含角色、操作者、前后版本与完整绑定集合，唯一tenant/role/to_version；应用成功变更后续与关联/版本在同一事务追加，不宣称已完成Service或审计验收。实体不继承逻辑删除BaseDo，JSON根数组由SQL校验，元素/存在性/归属仍由Service验证。详见docs/design/RBAC-ROLE-MENU-WRITE-STORAGE.md、docs/evidence/rbac-role-menu-write-storage-slice.json。SQL执行不扩大为助手运行编译/Maven/JUnit/guard或启动测试。

执行分工更新（DEC-046）：2026-10-24用户授权当前商城后续已确定的SQL由助手直接编写、预检、执行和核对，无需逐批请求常规SQL许可；下文早期“仅编写/需新增执行授权”限制由此覆盖。已落地的脚本不在本库重复执行，历史状态以分节最新执行更新和证据为准，手工执行步骤保留作记录/其他明确目标环境的操作参考。

## V007：租户套餐表（2026-10-24，已执行并核对元数据）

用户按DEC-048将本批四个套餐类交助手写，助手完成模型/Mapper/Service后按DEC-046核对MySQL8.0.43/QIANG/qinghe_system及同名表不存在，执行 [V007__create_system_tenant_package.sql](V007__create_system_tenant_package.sql)，退出0无警告；核对11列、3索引、4启用CHECK、id自增、name全局唯一且不含deleted、menu_ids非空JSON根数组、status无默认、auth_version默认0、0行，无tenant_id/外键。仅remark可空，name的唯一比较遵从utf8mb4_unicode_ci；空menu_ids不是通配权限。证据docs/evidence/tenant-package-table-created.json。

**本库不要重复执行V007；system目录V002～V007已落地。** 没有初始化套餐、菜单、账号或租户，没有运行非法数据反例、MyBatis、Service或认证测试。套餐停用仅禁止新开通/切入；已有租户不因此统一失效。在用不可删、资源存在、升级降级传播及最终提交版本协议仍由后续实现和验证，不由DDL或auth_version默认0保证。

## V006：后台账号表（2026-10-24，已执行并核对元数据）

助手按DEC-046核对本机MySQL8.0.43/QIANG/qinghe_system及同名表不存在后执行 [V006__create_system_users.sql](V006__create_system_users.sql)，退出0、无警告；SHOW CREATE及information_schema核对21列、6索引、9启用CHECK、id自增、tenant_id与status无默认、auth_version默认0、0行，无外键或账号种子。证据docs/evidence/admin-user-table-created.json，设计docs/design/ADMIN-USER-TABLE.md。**本库不要重复执行V006或旧根V001员工草稿。** 当前system目录V002～V006均已落地，不递归套用其他服务同版本脚本。

相对旧根V001未执行稿：增加auth_version；post_ids改JSON；时间统一DATETIME(6)；username明确ascii_bin及4～30字母数字，删除不释放的(tenant_id,username)唯一键不变；password容量255；status明确提供，增加字段取值/JSON形状CHECK。生产模型、输入校验和登录查询须遵守相同大小写/字符规则；CHECK不验证实际密码编码或JSON数组元素/关联归属。

仅建表与元数据检查，不包含账号/密码初始化、非法写入反例或MyBatis/Service/认证测试；不宣称额度并发、部门岗位归属、五秒撤权或权限验收完成。

## V005：租户主表（2026-10-24，已执行并核对元数据）

执行更新（DEC-046）：助手已在核对qinghe_system及同名表不存在后执行V005，退出0无警告；核对16列、4个索引、7项启用CHECK、仅contact_user_id可NULL、auth_version默认0、无tenant_id/外键，0行，见docs/evidence/tenant-table-created.json。**本库不要重复执行V005/V004/V003/V002。** 脚本头部及下文未执行表述为编写阶段记录，以本更新为准。未插入租户/用户/套餐或运行Mapper/Service/非法写入测试。

用户已按方案手写TenantDO并继续。助手按DEC-017编写 [V005__create_system_tenant.sql](V005__create_system_tenant.sql)，对应11本类字段及5继承字段，共16列；主键、code唯一键、套餐批量查找索引(package_id,id)、到期扫描索引(expire_time,id)，7项CHECK。全局注册表无tenant_id，不建外键，不创建租户/账号/套餐/平台id0记录。本机只读确认MySQL8.0.43/qinghe_system，system_tenant/system_tenant_package/system_users目前均不存在；未执行本脚本。

执行前在SQL客户端选中qinghe_system并确认：

```sql
SELECT DATABASE(), VERSION();
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_tenant';
```

执行记录中的库和表预检必须先完成；同名表已存在则核对，不重建或加IF NOT EXISTS掩盖旧结构。本库V002/V003/V004/V005现均已执行，不为后续步骤重复运行。常规SQL后续按DEC-046由助手完成执行和核对。

执行后检查：

```sql
SHOW CREATE TABLE qinghe_system.system_tenant;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'qinghe_system' AND TABLE_NAME = 'system_tenant'
ORDER BY ORDINAL_POSITION;

SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'qinghe_system' AND TABLE_NAME = 'system_tenant'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE, ENFORCED
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'qinghe_system' AND TABLE_NAME = 'system_tenant';

SELECT COUNT(*) AS row_count FROM qinghe_system.system_tenant;
```

预期16列、AUTO主键、4个索引、7项启用CHECK，无tenant_id/外键，0行。只有contact_user_id允许NULL；contact_name/mobile默认空串，创建租户后同服务事务建立管理员并校验其归属再补contact_user_id，不能把NULL允许当作正式开通永远没有管理员。status/package_id/expire_time/account_count必须由服务端明确给出，auth_version默认0，审计默认值沿用当前迁移风格。

商家编码全局唯一、不把deleted放进唯一键；正则拒绝任何非小写字母/数字/短横线字符，另限制3～32长度，不借助可能匹配末尾换行的$锚点。name不建唯一键，允许品牌显示名相同。数据库唯一键不会自动禁止UPDATE修改code，后续更新VO/Service白名单必须禁止改code或通过改码释放旧标识；auth_version自增与并发最终提交校验也尚未实现，不能由默认0宣称撤权完成。

package_id/contact_user_id只有数值及空值约束，真实存在/所属租户/有效套餐由Service验证；物理外键按项目约定不建。租户运营停用/到期不删除历史交易，B02保留本人历史订单/合法售后及受限履约退款，按操作能力授权；数据库不把expire_time要求为未来，也不依赖任务及时改status。配额调整需事务内验证未删除员工数量含禁用/管理员，account_count不是实时人数。

SQL按 [MySQL CHECK规则](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html) 和 [正则函数说明](https://dev.mysql.com/doc/refman/8.0/en/regexp.html) 编写：自增id不参与CHECK，不用NOW()等非确定函数约束套餐到期。尚未执行CREATE/非法数据反例、运行Mapper/Service或编译测试；只做字段/语义静态核对，不能报告DDL或认证验收通过。

## V004：第一方客户端初始配置（2026-10-24，已执行并核对数据）

执行更新（DEC-045）：用户明确“帮我执行sql”，助手在确认qinghe_system总0行及三个标识不存在后执行V004，退出0无警告。只读核对总3行，平台/商家/消费者realm对应、status0、access900/refresh604800、scopes=[]、deleted0、actor bootstrap，见docs/evidence/oauth2-client-seeds-created.json。**本库不要重复执行V004，也不重跑V003/V002。** 脚本头部与下文未执行表述为编写时记录，以本更新为准。只初始化应用配置，未运行Mapper/Service或认证测试，未创建账号/Token/角色/密钥。

脚本 [V004__seed_first_party_oauth2_clients.sql](V004__seed_first_party_oauth2_clients.sql) 由助手按DEC-017编写，只有一条多行INSERT，新增qinghe-platform/platform、qinghe-admin/merchant、qinghe-app/consumer三个已设计的应用。三行均status=0（开启），访问Token初值900秒（15分钟），刷新Token初值604800秒（7天），scopes为JSON空数组；时间初值由助手按DEC-022选定，非用户逐项确认或上线安全验收。后续签发读取这些值，实际Cookie闲置/绝对会话时长另按Gateway契约配置，不用刷新期限冒充Cookie期限。Redis校验快照仍最多5秒，不随Token有效期增大。

仅应用配置，不初始化用户/管理员/租户/Token/角色/密钥，不授予通配scope。空scope意味着没有额外OAuth范围，不等于绕过RBAC。clientId只寻址，真实签发入口认证、三端绑定及账号/租户/member资格检查仍待实现，不能因应用配置启用就宣布可登录。

助手本轮只读确认qinghe_system当前总记录0、三个保留clientId均不存在，未执行INSERT。DEC-044只授权V003建表，不自动覆盖种子数据写入。由用户自行执行，或后续明确要求助手执行本文件。

执行前在SQL客户端选中qinghe_system，检查三个ID（含逻辑删除记录，不加deleted过滤）：

```sql
SELECT DATABASE();
SELECT client_id, realm, status, deleted+0 AS deleted
FROM qinghe_system.system_oauth2_client
WHERE client_id IN ('qinghe-platform', 'qinghe-admin', 'qinghe-app');
```

目标库正确且没有这三个标识后，执行V004；若任一已存在则先核对，不改为INSERT IGNORE或ON DUPLICATE KEY UPDATE覆盖现有配置/恢复逻辑删除。该脚本不重跑V003、不带USE/UPDATE/DELETE。单个多行INSERT在当前InnoDB表中统一成功或因约束拒绝整条语句；不包含独立多条写入或额外事务提交。

执行后只读核对：

```sql
SELECT client_id, realm, status,
       access_token_validity_seconds, refresh_token_validity_seconds,
       scopes, deleted+0 AS deleted
FROM qinghe_system.system_oauth2_client
WHERE client_id IN ('qinghe-platform', 'qinghe-admin', 'qinghe-app')
ORDER BY client_id;
```

预期精确3条、对应realm、status0、时长900/604800、scopes为[]、deleted0。本轮未执行脚本/约束反例/Mapper或Service测试；客户端真实资格分支和完整认证仍未验收。

## V003：客户端配置表（2026-10-24，已执行并核对元数据）

执行更新（DEC-044）：用户明确“建表你建”，助手已在本机qinghe_system执行V003，退出0无警告，核对13列/自增主键/client_id唯一键/7项启用CHECK、无tenant_id与外键，0行，见docs/evidence/oauth2-client-table-created.json。**本库不要重复执行V003，也不需要重跑V002。** 脚本头部及下文未执行表述为编写时记录，以本更新为准。只完成DDL和元数据检查，未插入客户端数据或运行MyBatis/业务测试；执行步骤保留作迁移记录。

用户已按字段方案手写OAuth2ClientDO并要求下一步。助手按DEC-017编写 [V003__create_oauth2_client.sql](V003__create_oauth2_client.sql)，逐项映射8个本类字段和5个BaseDo字段，共13列；主键/唯一键各1个、7项CHECK，无tenant_id、外键、选库或种子数据。仅已知全局客户端实体忽略租户，其他业务表隔离不变。来源和realm增量见 [字段设计](../../../../docs/design/OAUTH2-CLIENT-DATA.md)。

助手已只读查询本机127.0.0.1:3306：MySQL 8.0.43，qinghe_system存在，system_oauth2_client表数量0；没有执行CREATE/INSERT或修改数据库。读取时间2026-10-24。现有qinghe-local login-path用于认证，未读取或输出密码。该观察不替代用户执行时重新确认。

**用户执行步骤：**

1. 在SQL客户端连接本机MySQL并选中qinghe_system，不选参考项目或测试库。先执行：

```sql
SELECT DATABASE(), VERSION();

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'system_oauth2_client';
```

2. 确认DATABASE()为qinghe_system且表不存在后，执行V003文件。它只有一条CREATE，没有IF NOT EXISTS；若表已存在则先核对结构，不删除/覆盖，不为继续步骤重复执行已落地的V002。
3. 执行后检查：

```sql
SHOW CREATE TABLE qinghe_system.system_oauth2_client;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'qinghe_system'
  AND TABLE_NAME = 'system_oauth2_client'
ORDER BY ORDINAL_POSITION;

SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE, ENFORCED
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'qinghe_system'
  AND TABLE_NAME = 'system_oauth2_client';

SELECT COUNT(*) AS row_count
FROM qinghe_system.system_oauth2_client;
```

预期13列、AUTO_INCREMENT主键、client_id唯一、7项启用CHECK，空表0行，无tenant_id/外键。JSON约束仅保证根节点为数组，元素字符串、空白/重复scope和允许范围由后续服务真实校验；不是已验证的JSON往返。realm转二进制精确比对以拒绝大小写/尾空格别名；client_id使用ascii_bin，应用仍须校验完整标识格式，不依赖VARCHAR尾空格比较作为凭据语义。

status/realm/两个时长/scopes无默认值，写配置时必须明确指定；刷新时长大于访问时长。scopes空范围用JSON数组[]而不是SQL NULL，不自动填充通配权限。唯一键不包含deleted，删除后不让新行复用旧应用标识，恢复/启用流程后续设计。DO审计自动填充尚未实现，数据库默认值不意味着显式传入NULL也能成功，后续夹具及服务应明确处理。

**状态：SQL已编写并静态核对，未执行、未做MySQL语法/约束实测、未运行编译或测试。** 本批只建表，不创建客户端三条初始记录、签发Token或接入Security。建表后再写Client Mapper/检查服务；Service资格checker尚无真实实现，认证和5秒撤权未验收。DEC-041仅授权此前Token两表执行，不扩展到本表；此处是用户自行执行说明。

## V002：Token 两表迁移

执行更新：2026-10-23用户明确要求助手建表检查（DEC-041），已在本机qinghe_system执行该脚本退出0，并核对双自增、9个索引（含主键）、14个启用CHECK、无外键及两表0行。证据docs/evidence/token-tables-created.json。下文未执行状态为编写时记录，以本条为准；只完成DDL与元数据检查，未插入测试数据、运行MyBatis/业务测试。不要在同一库重复执行脚本。

2026-10-23 / TASK-012 / REQ-001、012。SQL由助手按DEC-017编写，生产DO/POM由用户写。脚本V002__create_oauth2_tokens.sql尚未执行、未做MySQL语法/结构/约束实测；源码字段映射仅静态核对。

## 范围与对应

目标为本机新商城的qinghe_system schema，不是参考yudao或其他项目数据库。只新建system_oauth2_refresh_token和system_oauth2_access_token，不创建schema、不改账号、不初始化用户或Token、不带USE/外键/删表/重建操作。当前V001员工表草稿在父目录，本目录不是独立可盲目扫描的完整Flyway迁移集；尚未引Flyway/Liquibase，V002为人工阶段脚本名，未来配置迁移位置需统一已执行基线与服务目录，不同时扫描多个服务的V002。

映射用户两个OAuth2 DO和继承的TenantBaseDo/BaseDo字段：自增id、JSON集合、有效期、主动撤销、归属与审计/逻辑删除。当前数据库MySQL8.0.43满足CHECK实际执行所需8.0.16+；未在其他版本试跑。

- 令牌值用ascii_bin和唯一索引，区分大小写；deleted不参与唯一键。VARCHAR的尾空格比较不作为凭据等价设计依据，服务端签发/解析只接受约定无空白随机串。
- JSON列非空且根形状分别为ARRAY/OBJECT；内部字符串元素及业务范围仍由服务校验。scopes传空List，userInfo传空Map，不用SQL NULL代表空容器。
- expires_time、revoked_time使用微秒DATETIME(6)，MySQL不保留Java纳秒精度；签发/回源时间规范后续统一。
- revoked_time与deleted职责不同；撤销服务必须同步处理相关访问/刷新资格，不仅删除Redis。
- 租户/userType/user/client联合索引用于作用域查询；全局唯一令牌查询仅供后续受控认证查找，不授权按请求tenant-id跳过归属校验。
- Access.refresh_token逻辑关联Refresh.refresh_token；user/tenant/type/client匹配、事务、并发撤销/刷新与账号状态由Service完成，无物理外键不等于允许不一致关联。
- 记录仍沿yudao存储令牌值，DB权限/备份/导出不可当普通业务数据开放；本稿不冒称已实现凭据摘要存储或完整OAuth2授权服务器。

## 用户执行步骤

先用自己的SQL客户端连接127.0.0.1:3306并选中qinghe_system。执行下列只读检查：

```sql
SELECT DATABASE(), VERSION();

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('system_oauth2_access_token', 'system_oauth2_refresh_token');
```

确认schema为qinghe_system、两表不存在后，打开同目录V002__create_oauth2_tokens.sql执行。若发现已有同名表，先停止并对比结构，不删表、不加IF NOT EXISTS掩盖差异。MySQL DDL隐式提交，不保证两个CREATE一起回滚；中途失败先检查哪些表已存在，不直接全文件重复执行。

执行后核对：

```sql
SHOW CREATE TABLE qinghe_system.system_oauth2_refresh_token;
SHOW CREATE TABLE qinghe_system.system_oauth2_access_token;
```

本批不需要创建测试令牌或运行Mapper查询。建表成功只证明DDL落地；AUTO回填、JSON TypeHandler/逻辑删除/租户条件和异常约束需后续独立测试，不能借旧Redis23项通过推断。本说明提供用户自行执行步骤，不是助手获准执行迁移。

## 下一批

用户确认结构落地后，先建Mapper基础接口；认证查询需先设计可信凭据查找与租户引导，不能直接在尚未认证时把请求tenant-id当可信上下文。JsonUtils明确类型缓存/独立DTO快照、key约定、DAO与撤销一致性继续分批推进；system尚不接入缺真实API实现的security。


## 2026-10-27：V012/V013已执行，不再重复运行

本机qinghe_system / QIANG / MySQL8.0.43，执行前列名、表名与约束名冲突检查为空。
V012为system_users和system_oauth2_refresh_token增加credential_version bigint NOT NULL DEFAULT 0与各一项启用CHECK；两表仍0行。
V013创建system_dept：12列、2索引、6项启用CHECK、0行；不创建部门/账号/密码/角色/授权。
DDL隐式提交；迁移成功只代表结构落地。源码未编译，本批测试用户已暂缓，不能把迁移成功当员工/授权/会话行为通过。
元数据和脚本摘要见docs/evidence/management-user-credential-migration.json与management-department-migration.json。


## 2026-10-27：V014～V016已执行，禁止重复执行

- V014 system_tenant_package_change：10列、3索引、5项启用CHECK、0行，只保存同事务成功结果及请求摘要，不保存密码/Token。
- V015 system_menu.auth_version bigint非空默认0与chk_menu_auth_version启用。
- V016明确资源目录55项，平台35/商家20，父链同端完整；没有创建任何账号、角色、绑定或默认权限。账号/角色/两类绑定表仍0行，平台管理员引用仍为空。

执行环境均为本机qinghe_system/QIANG/MySQL8.0.43，均先核对对象/数据冲突。脚本摘要与元数据分别见management-package-result-migration.json、management-menu-version-migration.json、management-resource-catalog-initialized.json。首次平台管理员工具只编写未执行，步骤见docs/design/MANAGEMENT-SETUP-AND-REVIEW.md；需要用户私下输入密码，不把它放到迁移SQL或聊天。


2026-10-28：V021__product_transfer_resource.sql已在本机qinghe_system预检执行，新增210405商品归属转移按钮及1条MENU.SEED direct-sql审计，菜单85。未给角色/套餐自动授权，无租户/商品业务初始化。证据docs/evidence/product-transfer-migrations-created.json（相对正式工作区）。


2026-10-28：V022__product_export_resource.sql已在本机qinghe_system预检执行，新增210406商品导出按钮与1条direct-sql初始化审计，菜单86，无角色/套餐扩权。product/V007创建导出生成记录表8列2索引6CHECK，0行；完整SQL元数据见正式工作区docs/evidence/product-export-migrations-created.json。


2026-10-28：V023__tenant_entry.sql已在本机qinghe_system预检执行，入口表17列3索引10启用CHECK/0行，全局host唯一且逻辑删除不释放。无域名/证明/租户业务初始化、菜单或授权改变；真实域名验证管理待后续，元数据见docs/evidence/storefront-entry-schema-created.json。
