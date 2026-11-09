-- DEC-022/046/062. New append-only application audit for system management lifecycles.
-- Preflight table/constraint names absent. No passwords, tokens or privacy snapshots.
CREATE TABLE `system_management_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL,
    `actor_tenant_id` bigint NOT NULL,
    `actor_user_id` bigint NOT NULL,
    `action` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `target_id` bigint NOT NULL,
    `from_version` bigint NOT NULL,
    `to_version` bigint NOT NULL,
    `details` json NOT NULL COMMENT '服务端白名单摘要，不存密码、凭据和个人联系信息',
    `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    KEY `idx_management_audit_tenant` (`tenant_id`, `create_time`, `id`),
    KEY `idx_management_audit_actor` (`actor_tenant_id`, `actor_user_id`, `create_time`, `id`),
    CONSTRAINT `chk_mga_tenant` CHECK (`tenant_id` >= 0 AND `actor_tenant_id` >= 0),
    CONSTRAINT `chk_mga_actor` CHECK (`actor_user_id` > 0),
    CONSTRAINT `chk_mga_target` CHECK (`target_id` > 0),
    CONSTRAINT `chk_mga_action` CHECK (CHAR_LENGTH(TRIM(`action`)) > 0),
    CONSTRAINT `chk_mga_version` CHECK (`from_version` >= -1 AND `from_version` < 9223372036854775807 AND `to_version` = `from_version` + 1),
    CONSTRAINT `chk_mga_details` CHECK (JSON_TYPE(`details`) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台管理成功变更审计，创建版本-1到0，应用只追加';
