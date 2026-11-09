-- DEC-021/022/046/062. Successful package changes and their role updates commit together.
-- A missing result is not a success; retry uses the same operation_id and request fingerprint.
CREATE TABLE `system_tenant_package_change` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `package_id` bigint NOT NULL,
    `actor_user_id` bigint NOT NULL,
    `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `from_version` bigint NOT NULL,
    `to_version` bigint NOT NULL,
    `changed` bit(1) NOT NULL,
    `impact` json NOT NULL COMMENT '影响租户与角色/授权增减摘要，无凭据及联系人快照',
    `create_time` datetime(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_package_change_operation` (`operation_id`),
    KEY `idx_package_change_history` (`package_id`, `create_time`, `id`),
    CONSTRAINT `chk_package_change_ids` CHECK (`package_id` > 0 AND `actor_user_id` > 0),
    CONSTRAINT `chk_package_change_operation` CHECK (REGEXP_LIKE(`operation_id`, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT `chk_package_change_hash` CHECK (REGEXP_LIKE(`request_hash`, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT `chk_package_change_version` CHECK (`from_version` >= 0 AND `to_version` >= `from_version` AND `to_version` - `from_version` = CAST(`changed` AS UNSIGNED)),
    CONSTRAINT `chk_package_change_impact` CHECK (JSON_TYPE(`impact`) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐同步成功结果，幂等提交与失败后重新确认，不是异步任务队列';
