-- yudao infra_file / infra_file_config adapted for the current tenant-aware mall.
-- Target: local QIANG / MySQL 8.0.43. New database only; no existing schema/data is overwritten.
-- DDL implicitly commits in MySQL. Inspect information_schema before retrying any partial execution.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 10;
CREATE TEMPORARY TABLE qinghe_infra_create_guard (
    ok TINYINT NOT NULL CHECK (ok = 1)
) ENGINE=InnoDB;
INSERT INTO qinghe_infra_create_guard(ok)
SELECT IF(@@hostname = 'QIANG' AND VERSION() LIKE '8.0.%'
    AND DATABASE() = 'qinghe_system'
    AND NOT EXISTS (SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'qinghe_infra'), 1, 0);

CREATE DATABASE qinghe_infra CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qinghe_infra;

CREATE TABLE infra_file_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    storage INT NOT NULL,
    remark VARCHAR(256) NULL,
    master BIT(1) NOT NULL DEFAULT b'0',
    config JSON NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    master_slot TINYINT GENERATED ALWAYS AS
        (CASE WHEN master = b'1' AND deleted = b'0' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_infra_file_config_master (master_slot),
    CONSTRAINT ck_infra_file_config_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_infra_file_config_storage CHECK (storage = 10),
    CONSTRAINT ck_infra_file_config_json CHECK (JSON_TYPE(config) = 'OBJECT'),
    CONSTRAINT ck_infra_file_config_local CHECK (
        JSON_CONTAINS_PATH(config, 'all', '$."@class"', '$.basePath', '$.domain') = 1
        AND JSON_UNQUOTE(JSON_EXTRACT(config, '$."@class"')) =
            'cn.qinghe.mall.module.infra.framework.file.core.client.local.LocalFileClientConfig'
        AND JSON_TYPE(JSON_EXTRACT(config, '$.basePath')) = 'STRING'
        AND CHAR_LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(config, '$.basePath')))) > 0
        AND JSON_TYPE(JSON_EXTRACT(config, '$.domain')) = 'STRING'
        AND CHAR_LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(config, '$.domain')))) > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE infra_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    config_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    url VARCHAR(1024) NOT NULL,
    type VARCHAR(127) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_user_type INT NOT NULL,
    purpose VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_infra_file_path (config_id, path),
    KEY idx_infra_file_tenant_owner (tenant_id, owner_user_type, owner_user_id, deleted, id),
    KEY idx_infra_file_tenant_time (tenant_id, create_time),
    CONSTRAINT ck_infra_file_tenant CHECK (tenant_id >= 0),
    CONSTRAINT ck_infra_file_config CHECK (config_id > 0),
    CONSTRAINT ck_infra_file_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_infra_file_size CHECK (size > 0 AND size <= 104857600),
    CONSTRAINT ck_infra_file_owner CHECK (owner_user_id > 0 AND owner_user_type IN (1, 2)),
    CONSTRAINT ck_infra_file_purpose CHECK (purpose IN ('product-image', 'store-logo', 'aftersale-evidence')),
    CONSTRAINT ck_infra_file_image CHECK (type IN ('image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp')),
    CONSTRAINT ck_infra_file_path CHECK (path LIKE CONCAT('tenant/', tenant_id, '/', purpose, '/%')),
    CONSTRAINT ck_infra_file_public_actor CHECK (purpose = 'aftersale-evidence' OR owner_user_type = 2),
    CONSTRAINT ck_infra_file_platform CHECK (tenant_id > 0 OR purpose = 'store-logo')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Necessary local application configuration, not a tenant/account/business initialization.
-- No directory/file is created by this script; application file IO remains unverified.
START TRANSACTION;
INSERT INTO infra_file_config
    (id, name, storage, remark, master, config, creator, updater)
VALUES
    (1, 'local-file-storage', 10, 'Local development storage; change via controlled configuration before deployment', b'1',
     JSON_OBJECT('@class', 'cn.qinghe.mall.module.infra.framework.file.core.client.local.LocalFileClientConfig',
                 'basePath', 'D:/aaa/a-yudao/mall-saas/runtime/files',
                 'domain', 'http://127.0.0.1:8088'),
     'sql-infra-bootstrap', 'sql-infra-bootstrap');
COMMIT;

SELECT 'INFRA_FILE_SCHEMA_CREATED' AS result,
       (SELECT COUNT(*) FROM infra_file_config) AS config_rows,
       (SELECT COUNT(*) FROM infra_file) AS file_rows;
