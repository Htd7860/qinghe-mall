-- Author: assistant, DEC-017. Field plan: docs/design/OAUTH2-CLIENT-DATA.md.
-- User has written the matching OAuth2ClientDO. This migration has NOT been executed.
-- MySQL 8.0.16+ for enforced CHECK; local server read-only verified as 8.0.43.
-- Select qinghe_system in your SQL client before executing. No USE, seed data or credentials.
-- CREATE only. MySQL DDL implicitly commits; do not rerun against an existing table.

CREATE TABLE `system_oauth2_client` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户端配置编号',
    `client_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局唯一应用标识，由服务端选择，不是认证密钥',
    `name` varchar(128) NOT NULL COMMENT '应用名称',
    `status` tinyint NOT NULL COMMENT '状态：0开启，1关闭，创建时明确指定',
    `realm` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用入口：platform、merchant、consumer',
    `access_token_validity_seconds` int NOT NULL COMMENT '访问令牌有效期，单位秒，必须为正数',
    `refresh_token_validity_seconds` int NOT NULL COMMENT '刷新令牌有效期，单位秒，大于访问令牌有效期',
    `scopes` json NOT NULL COMMENT '客户端允许的OAuth范围，字符串数组；不是RBAC角色权限',
    `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者标识，由服务端维护',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者标识，由服务端维护',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除，客户端标识不因删除而复用',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_client_client_id` (`client_id`),
    CONSTRAINT `chk_oauth2_client_id` CHECK (
        CHAR_LENGTH(`client_id`) > 0
        AND CHAR_LENGTH(`client_id`) = CHAR_LENGTH(TRIM(`client_id`))
    ),
    CONSTRAINT `chk_oauth2_client_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `chk_oauth2_client_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_oauth2_client_realm` CHECK (
        CAST(`realm` AS BINARY) IN ('platform', 'merchant', 'consumer')
    ),
    CONSTRAINT `chk_oauth2_client_access_validity` CHECK (`access_token_validity_seconds` > 0),
    CONSTRAINT `chk_oauth2_client_refresh_validity` CHECK (
        `refresh_token_validity_seconds` > `access_token_validity_seconds`
    ),
    CONSTRAINT `chk_oauth2_client_scopes` CHECK (JSON_TYPE(`scopes`) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第一方OAuth2客户端全局配置';

