-- DEC-046/065, B04. Local qinghe_product only. No stock/product seed rows.
-- Two committed-success ledger tables; failures roll back operation + lines + stock together.
-- ORDER/SECKILL stock protocols will be added separately; this slice only initializes/adjusts.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_stock_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_stock_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_spu','product_sku','product_catalog_mutex','product_catalog_audit') AND ENGINE='InnoDB')=4
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_stock_operation','product_stock_record')),1,0);

CREATE TABLE product_stock_operation (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 operation_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 spu_id BIGINT NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_user_id BIGINT NOT NULL,
 reason VARCHAR(512) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_stock_operation(tenant_id,operation_key),
 KEY idx_product_stock_operation_spu(tenant_id,spu_id,id),
 CONSTRAINT ck_product_stock_operation_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_stock_operation_identity CHECK(spu_id>0 AND actor_user_id>0),
 CONSTRAINT ck_product_stock_operation_kind CHECK(kind IN('INITIALIZE','ADJUST')),
 CONSTRAINT ck_product_stock_operation_key CHECK(operation_key REGEXP '^(manual|sku-init):[A-Za-z0-9:_-]+$'),
 CONSTRAINT ck_product_stock_operation_hash CHECK(request_hash REGEXP '^[0-9a-f]{64}$'),
 CONSTRAINT ck_product_stock_operation_reason CHECK(CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_stock_record (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 operation_id BIGINT NOT NULL,
 spu_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 init_sku_id BIGINT GENERATED ALWAYS AS (CASE WHEN kind='INITIALIZE' THEN sku_id ELSE NULL END) STORED,
 delta BIGINT NOT NULL,
 before_stock BIGINT NOT NULL,
 after_stock BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_stock_record(tenant_id,operation_id,sku_id),
 UNIQUE KEY uk_product_stock_initial_once(tenant_id,init_sku_id),
 KEY idx_product_stock_record_sku(tenant_id,sku_id,id),
 CONSTRAINT ck_product_stock_record_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_stock_record_identity CHECK(operation_id>0 AND spu_id>0 AND sku_id>0),
 CONSTRAINT ck_product_stock_record_kind CHECK(kind IN('INITIALIZE','ADJUST')),
 CONSTRAINT ck_product_stock_record_flow CHECK((kind='INITIALIZE' AND before_stock=0 AND delta>=0) OR (kind='ADJUST' AND delta<>0)),
 CONSTRAINT ck_product_stock_record_delta CHECK(delta BETWEEN -2147483647 AND 2147483647),
 CONSTRAINT ck_product_stock_record_before CHECK(before_stock BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_stock_record_after CHECK(after_stock BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_stock_record_balance CHECK(after_stock=before_stock+delta)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'PRODUCT_STOCK_LEDGER_CREATED' AS result;
