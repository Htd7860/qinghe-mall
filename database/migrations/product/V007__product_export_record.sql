-- DEC-046/065: successful file-generation evidence only, not download completion.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_export_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_export_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_spu' AND ENGINE='InnoDB')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_export_record'),1,0);
CREATE TABLE product_export_record (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 tenant_id BIGINT NOT NULL,
 actor_user_id BIGINT NOT NULL,
 filters JSON NOT NULL,
 row_count INT NOT NULL,
 byte_count INT NOT NULL,
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 KEY idx_product_export_actor(tenant_id,actor_user_id,create_time),
 CONSTRAINT ck_product_export_identity CHECK(tenant_id>0 AND actor_user_id>0),
 CONSTRAINT ck_product_export_uuid CHECK(id REGEXP '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$'),
 CONSTRAINT ck_product_export_rows CHECK(row_count>=0 AND row_count<=20000),
 CONSTRAINT ck_product_export_bytes CHECK(byte_count>0 AND byte_count<=33554432),
 CONSTRAINT ck_product_export_filters CHECK(JSON_TYPE(filters)='OBJECT'),
 CONSTRAINT ck_product_export_hash CHECK(sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'PRODUCT_EXPORT_RECORD_CREATED' AS result;
