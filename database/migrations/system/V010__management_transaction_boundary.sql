-- DEC-022/046/062: back-office configuration mutex and explicit administrator-role pointer.
-- Preflight both new table and system_tenant.admin_role_id absent. No existing row overwritten.
-- The singleton is infrastructure configuration, not a user/role or permission grant.
CREATE TABLE `system_management_mutex` (
    `id` tinyint NOT NULL,
    `platform_admin_user_id` bigint DEFAULT NULL,
    `platform_admin_role_id` bigint DEFAULT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_management_mutex_id` CHECK (`id` = 1),
    CONSTRAINT `chk_management_mutex_user` CHECK (`platform_admin_user_id` IS NULL OR `platform_admin_user_id` > 0),
    CONSTRAINT `chk_management_mutex_role` CHECK (`platform_admin_role_id` IS NULL OR `platform_admin_role_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台配置写事务共同入口及受保护平台管理员引用';

INSERT INTO `system_management_mutex` (`id`) VALUES (1);

ALTER TABLE `system_tenant`
    ADD COLUMN `admin_role_id` bigint DEFAULT NULL COMMENT '受保护管理员角色，可信开通事务设置，不依据role code提权' AFTER `contact_user_id`,
    ADD CONSTRAINT `chk_tenant_admin_role` CHECK (`admin_role_id` IS NULL OR `admin_role_id` > 0);
