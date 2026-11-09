# Member migrations

V001__member_user_address.sql已按DEC-046在本机QIANG/MySQL8.0.43预检后创建新qinghe_member库和会员/地址/不含PII变更记录三表。无账号、密码、地址或数据库权限初始化。DDL隐式提交；不重复执行，重试前核对现有schema和表。

元数据见正式工作区docs/evidence/member-address-schema-created.json；没有运行真实注册/认证/地址业务或并发测试。独立服务仅使用本库，未来其他服务经受保护RPC获取本人地址快照，不直接访问这些Mapper。


V002__sms_code_ledger.sql已预检执行：member_sms_subject7列2索引3CHECK、member_sms_code18列3索引13CHECK，0行。无验证码、号码额度或会员种子；未执行实际发送、消费、并发/回滚测试，元数据见正式工作区docs/evidence/member-sms-ledger-schema-created.json。

V003__member_account_audit.sql已按DEC-046预检执行：9列、3索引、4项启用CHECK、0行；无手机号/密码/验证码/Token列、无业务初始化。不重复执行，元数据见docs/evidence/member-account-audit-schema-created.json。

V004__member_login_log.sql已按DEC-046预检执行：9列、4索引、6项启用CHECK、0行；日志不含明文手机/密码/验证码/Token/UA，无账号/登录记录初始化。不重复执行，元数据见docs/evidence/member-login-log-schema-created.json。
