-- DEC-046/065, REQ-002/B04. Adapted from yudao product catalog entities.
-- Local QIANG / MySQL 8.0.x. New database only; do not rerun against existing objects.
-- DDL implicitly commits. On partial failure inspect existing objects before continuing.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 10;
CREATE TEMPORARY TABLE product_catalog_create_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_catalog_create_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_system'
 AND NOT EXISTS(SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='qinghe_product'),1,0);
CREATE DATABASE qinghe_product CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qinghe_product;

CREATE TABLE product_category (
 id BIGINT NOT NULL AUTO_INCREMENT,
 parent_id BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(100) NOT NULL,
 pic_url VARCHAR(1024) NOT NULL DEFAULT '',
 sort INT NOT NULL DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 0,
 description VARCHAR(512) NOT NULL DEFAULT '',
 auth_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 active_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN name ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_category_name(tenant_id,parent_id,active_name),
 KEY idx_product_category_tree(tenant_id,deleted,parent_id,sort,id),
 CONSTRAINT ck_product_category_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_category_parent CHECK(parent_id>=0),
 CONSTRAINT ck_product_category_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_product_category_status CHECK(status IN(0,1)),
 CONSTRAINT ck_product_category_sort CHECK(sort BETWEEN 0 AND 10000),
 CONSTRAINT ck_product_category_version CHECK(auth_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_brand (
 id BIGINT NOT NULL AUTO_INCREMENT,
 name VARCHAR(100) NOT NULL,
 pic_url VARCHAR(1024) NOT NULL DEFAULT '',
 sort INT NOT NULL DEFAULT 0,
 description VARCHAR(512) NOT NULL DEFAULT '',
 status TINYINT NOT NULL DEFAULT 0,
 auth_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 active_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN name ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_brand_name(tenant_id,active_name),
 KEY idx_product_brand_status(tenant_id,deleted,status,sort,id),
 CONSTRAINT ck_product_brand_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_brand_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_product_brand_status CHECK(status IN(0,1)),
 CONSTRAINT ck_product_brand_sort CHECK(sort BETWEEN 0 AND 10000),
 CONSTRAINT ck_product_brand_version CHECK(auth_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_property (
 id BIGINT NOT NULL AUTO_INCREMENT,
 name VARCHAR(100) NOT NULL,
 remark VARCHAR(512) NOT NULL DEFAULT '',
 type TINYINT NOT NULL DEFAULT 1 COMMENT '1 sales specification, 2 display attribute',
 status TINYINT NOT NULL DEFAULT 0,
 auth_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 active_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN name ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_property_name(tenant_id,type,active_name),
 KEY idx_product_property_status(tenant_id,deleted,type,status,id),
 CONSTRAINT ck_product_property_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_property_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_product_property_type CHECK(type IN(1,2)),
 CONSTRAINT ck_product_property_status CHECK(status IN(0,1)),
 CONSTRAINT ck_product_property_version CHECK(auth_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_property_value (
 id BIGINT NOT NULL AUTO_INCREMENT,
 property_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 remark VARCHAR(512) NOT NULL DEFAULT '',
 status TINYINT NOT NULL DEFAULT 0,
 auth_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 active_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN name ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_property_value_name(tenant_id,property_id,active_name),
 KEY idx_product_property_value_parent(tenant_id,deleted,property_id,status,id),
 CONSTRAINT ck_product_property_value_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_property_value_parent CHECK(property_id>0),
 CONSTRAINT ck_product_property_value_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_product_property_value_status CHECK(status IN(0,1)),
 CONSTRAINT ck_product_property_value_version CHECK(auth_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'PRODUCT_CATALOG_SCHEMA_CREATED' AS result;
