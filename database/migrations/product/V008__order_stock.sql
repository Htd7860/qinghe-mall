-- DEC-046/065, B04/B06. Product-local order stock results and movement details.
-- No product, stock, account or order seed writes; do not rerun after creation.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_order_stock_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_order_stock_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_spu','product_sku','product_stock_operation','product_stock_record') AND ENGINE='InnoDB')=4
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_order_stock','product_order_stock_item')),1,0);

CREATE TABLE product_order_stock (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 request_snapshot JSON NULL,
 status TINYINT NOT NULL,
 release_reason VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_order_stock(tenant_id,order_id),
 KEY idx_product_order_stock_state(tenant_id,status,updated_at,id),
 CONSTRAINT ck_product_order_stock_identity CHECK(tenant_id>0 AND order_id>0 AND user_id>0),
 CONSTRAINT ck_product_order_stock_status CHECK(status IN(0,1,2)),
 CONSTRAINT ck_product_order_stock_request CHECK(
  (status=0 AND request_hash IS NULL AND request_snapshot IS NULL)
  OR (status IN(1,2) AND request_hash IS NOT NULL AND request_hash REGEXP '^[0-9a-f]{64}$'
   AND request_snapshot IS NOT NULL AND JSON_TYPE(request_snapshot)='OBJECT')),
 CONSTRAINT ck_product_order_stock_reason CHECK(
  (status=1 AND release_reason IS NULL)
  OR (status IN(0,2) AND release_reason IS NOT NULL AND release_reason IN('CREATE_FAILED','UNPAID_CLOSED'))),
 CONSTRAINT ck_product_order_stock_time CHECK(updated_at>=created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_order_stock_item (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 operation_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 spu_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 unit_price INT NOT NULL,
 spu_version BIGINT NOT NULL,
 sku_version BIGINT NOT NULL,
 before_stock INT NOT NULL,
 after_stock INT NOT NULL,
 release_before_stock INT NULL,
 release_after_stock INT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_order_stock_line(tenant_id,operation_id,order_item_id),
 UNIQUE KEY uk_product_order_stock_sku(tenant_id,operation_id,sku_id),
 KEY idx_product_order_stock_item_sku(tenant_id,sku_id,id),
 CONSTRAINT ck_product_order_stock_item_identity CHECK(tenant_id>0 AND operation_id>0 AND order_item_id>0 AND spu_id>0 AND sku_id>0),
 CONSTRAINT ck_product_order_stock_item_quantity CHECK(quantity BETWEEN 1 AND 9999),
 CONSTRAINT ck_product_order_stock_item_price CHECK(unit_price BETWEEN 1 AND 2000000000),
 CONSTRAINT ck_product_order_stock_item_version CHECK(spu_version>=0 AND sku_version>=0),
 CONSTRAINT ck_product_order_stock_item_deduct CHECK(before_stock>=quantity AND after_stock=before_stock-quantity),
 CONSTRAINT ck_product_order_stock_item_release CHECK(
  (release_before_stock IS NULL AND release_after_stock IS NULL)
  OR (release_before_stock IS NOT NULL AND release_after_stock IS NOT NULL
   AND release_before_stock>=0 AND release_after_stock>=0
   AND CAST(release_after_stock AS SIGNED)=CAST(release_before_stock AS SIGNED)+quantity))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'PRODUCT_ORDER_STOCK_CREATED';
