-- Qinghe system 独立本地数据库。
-- 2026-10-21：用户明确授权助手创建数据库；本地阶段允许先使用现有 root。
-- 执行记录：2026-10-21，用户完成本机login-path后，助手在MySQL8.0.43执行成功。
-- 已核对：执行前无同名库；执行后字符集/排序规则符合下方声明，表数量为0。
-- 证据：docs/evidence/system-database-created.json；本文件不是可反复执行的迁移。
-- 仅新建此数据库，不修改已有数据库、不创建业务表、不导入参考数据、不修改账号。
-- 执行前先只读确认 qinghe_system 不存在；若已存在，检查归属后停止本创建步骤。

CREATE DATABASE `qinghe_system`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
