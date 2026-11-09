-- DEC-046/065 B04/B05. New local database only; no template/price sample is inserted.
-- MySQL DDL implicitly commits; inspect partial objects before any retry.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE qinghe_trade_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO qinghe_trade_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%'
 AND DATABASE()='qinghe_system'
 AND NOT EXISTS(SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='qinghe_trade'),1,0);
CREATE DATABASE qinghe_trade CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qinghe_trade;
CREATE TABLE trade_delivery_express_template (
 id BIGINT NOT NULL AUTO_INCREMENT,
 name VARCHAR(100) NOT NULL,
 active_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN name ELSE NULL END) STORED,
 charge_mode TINYINT NOT NULL,
 sort INT NOT NULL DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 0,
 auth_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_delivery_template_name(tenant_id,active_name),
 KEY idx_trade_delivery_template_status(tenant_id,deleted,status,sort,id),
 CONSTRAINT ck_trade_delivery_template_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_trade_delivery_template_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_trade_delivery_template_mode CHECK(charge_mode IN(1,2)),
 CONSTRAINT ck_trade_delivery_template_sort CHECK(sort BETWEEN 0 AND 10000),
 CONSTRAINT ck_trade_delivery_template_status CHECK(status IN(0,1)),
 CONSTRAINT ck_trade_delivery_template_version CHECK(auth_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE trade_delivery_express_template_charge (
 id BIGINT NOT NULL AUTO_INCREMENT,
 template_id BIGINT NOT NULL,
 area_ids JSON NOT NULL,
 charge_mode TINYINT NOT NULL,
 start_count DECIMAL(18,6) NOT NULL,
 start_price BIGINT NOT NULL,
 extra_count DECIMAL(18,6) NOT NULL,
 extra_price BIGINT NOT NULL,
 default_rule BIT(1) NOT NULL DEFAULT b'0',
 default_template_id BIGINT GENERATED ALWAYS AS (CASE WHEN default_rule=b'1' AND deleted=b'0' THEN template_id ELSE NULL END) STORED,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_delivery_charge_default(tenant_id,default_template_id),
 KEY idx_trade_delivery_charge_template(tenant_id,template_id,deleted,id),
 CONSTRAINT ck_trade_delivery_charge_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_trade_delivery_charge_template CHECK(template_id>0),
 CONSTRAINT ck_trade_delivery_charge_mode CHECK(charge_mode IN(1,2)),
 CONSTRAINT ck_trade_delivery_charge_counts CHECK(start_count>0 AND extra_count>0),
 CONSTRAINT ck_trade_delivery_charge_integer CHECK(charge_mode=2 OR (start_count=FLOOR(start_count) AND extra_count=FLOOR(extra_count))),
 CONSTRAINT ck_trade_delivery_charge_prices CHECK(start_price BETWEEN 0 AND 2147483647 AND extra_price BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_trade_delivery_charge_areas CHECK(JSON_TYPE(area_ids)='ARRAY' AND JSON_LENGTH(area_ids)<=500),
 CONSTRAINT ck_trade_delivery_charge_default CHECK((default_rule=b'1' AND JSON_LENGTH(area_ids)=0) OR (default_rule=b'0' AND JSON_LENGTH(area_ids)>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE trade_delivery_express_template_free (
 id BIGINT NOT NULL AUTO_INCREMENT,
 template_id BIGINT NOT NULL,
 area_ids JSON NOT NULL,
 free_price BIGINT NOT NULL,
 free_count BIGINT NOT NULL,
 default_rule BIT(1) NOT NULL DEFAULT b'0',
 default_template_id BIGINT GENERATED ALWAYS AS (CASE WHEN default_rule=b'1' AND deleted=b'0' THEN template_id ELSE NULL END) STORED,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_delivery_free_default(tenant_id,default_template_id),
 KEY idx_trade_delivery_free_template(tenant_id,template_id,deleted,id),
 CONSTRAINT ck_trade_delivery_free_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_trade_delivery_free_template CHECK(template_id>0),
 CONSTRAINT ck_trade_delivery_free_threshold CHECK(free_price BETWEEN 0 AND 2147483647 AND free_count BETWEEN 0 AND 2147483647 AND (free_price>0 OR free_count>0)),
 CONSTRAINT ck_trade_delivery_free_areas CHECK(JSON_TYPE(area_ids)='ARRAY' AND JSON_LENGTH(area_ids)<=500),
 CONSTRAINT ck_trade_delivery_free_default CHECK((default_rule=b'1' AND JSON_LENGTH(area_ids)=0) OR (default_rule=b'0' AND JSON_LENGTH(area_ids)>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'TRADE_DELIVERY_SCHEMA_CREATED' AS result;
