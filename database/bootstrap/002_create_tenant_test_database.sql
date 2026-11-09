-- 仅供本地租户集成测试，执行前确认连接为自己的本地 MySQL。
-- 2026-10-22助手按DEC-037授权执行，核对空库；不修改qinghe_system，不创建表或账号。
CREATE DATABASE IF NOT EXISTS qinghe_tenant_test
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
