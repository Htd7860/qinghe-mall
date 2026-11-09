-- Minimal department tree used by employee ownership and role data scope. No seed data.
CREATE TABLE `system_dept` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `name` varchar(30) NOT NULL,
    `parent_id` bigint NOT NULL DEFAULT 0,
    `sort` int NOT NULL DEFAULT 0,
    `status` tinyint NOT NULL DEFAULT 0,
    `auth_version` bigint NOT NULL DEFAULT 0,
    `creator` varchar(64) NOT NULL,
    `create_time` datetime(6) NOT NULL,
    `updater` varchar(64) NOT NULL,
    `update_time` datetime(6) NOT NULL,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_dept_tree` (`tenant_id`, `deleted`, `parent_id`, `sort`, `id`),
    CONSTRAINT `chk_dept_tenant` CHECK (`tenant_id` >= 0),
    CONSTRAINT `chk_dept_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `chk_dept_parent` CHECK (`parent_id` >= 0),
    CONSTRAINT `chk_dept_sort` CHECK (`sort` BETWEEN 0 AND 10000),
    CONSTRAINT `chk_dept_status` CHECK (`status` IN (0,1)),
    CONSTRAINT `chk_dept_version` CHECK (`auth_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同租户部门树，父链和引用由管理事务维护';
