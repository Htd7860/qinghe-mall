-- Author: assistant, DEC-022/046/057/058.
-- Target: local qinghe_system, MySQL 8.0.16+.
-- Preflight target/version, object and constraint names before execution; do not rerun.
-- One CREATE only. No DML/seeds, existing table ALTER, grants or destructive commands.
-- Successful role-menu changes will insert this row in the same transaction as bindings/version.

CREATE TABLE `system_role_menu_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL COMMENT '可信租户，平台0、商家正数',
    `role_id` bigint NOT NULL COMMENT '变更的角色，逻辑引用，审计保留不随角色删除',
    `actor_user_id` bigint NOT NULL COMMENT '当前认证的后台操作者，不能从请求任意指定',
    `from_version` bigint NOT NULL COMMENT '变更前角色授权版本',
    `to_version` bigint NOT NULL COMMENT '变更后版本，恰好递增1',
    `before_menu_ids` json NOT NULL COMMENT '变更前全部有效绑定ID集合，非GET裁剪回显',
    `after_menu_ids` json NOT NULL COMMENT '服务端完整校验后的目标绑定ID集合',
    `trace_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '追踪标识，未接入时为空',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu_audit_version` (`tenant_id`, `role_id`, `to_version`),
    KEY `idx_role_menu_audit_actor` (`tenant_id`, `actor_user_id`, `create_time`, `id`),
    CONSTRAINT `chk_rma_tenant` CHECK (`tenant_id` >= 0),
    CONSTRAINT `chk_rma_role` CHECK (`role_id` > 0),
    CONSTRAINT `chk_rma_actor` CHECK (`actor_user_id` > 0),
    CONSTRAINT `chk_rma_version` CHECK (`from_version` >= 0 AND `from_version` < 9223372036854775807),
    CONSTRAINT `chk_rma_step` CHECK (`to_version` = `from_version` + 1),
    CONSTRAINT `chk_rma_before` CHECK (JSON_TYPE(`before_menu_ids`) = 'ARRAY'),
    CONSTRAINT `chk_rma_after` CHECK (JSON_TYPE(`after_menu_ids`) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单成功变更审计，应用只追加，无逻辑删除字段';
