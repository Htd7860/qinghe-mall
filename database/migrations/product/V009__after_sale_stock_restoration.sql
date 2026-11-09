-- DEC-046/057/065/066. Original aftersale stock receipt and cumulative original deduction bound.
-- Additive schema only; no SKU/stock/order/aftersale business writes or seed data.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_stock_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO after_sale_stock_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_order_stock_item')=14
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_order_stock')=10
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_after_sale_stock')
 AND NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME IN(
  'ck_product_order_item_restored','ck_product_after_sale_owner','ck_product_after_sale_item','ck_product_after_sale_quantity','ck_product_after_sale_movement')),1,0);
ALTER TABLE product_order_stock_item
 ADD COLUMN restored_count INT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_product_order_item_restored CHECK(restored_count BETWEEN 0 AND quantity
  AND (release_before_stock IS NULL OR restored_count=0));
CREATE TABLE product_after_sale_stock (
 tenant_id BIGINT NOT NULL,
 after_sale_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 operation_id BIGINT NOT NULL,
 spu_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 before_stock INT NOT NULL,
 after_stock INT NOT NULL,
 create_time DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,after_sale_id),
 KEY idx_product_after_sale_item(tenant_id,operation_id,order_item_id),
 CONSTRAINT ck_product_after_sale_owner CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND after_sale_id BETWEEN 1 AND 9007199254740991
  AND order_id BETWEEN 1 AND 9007199254740991 AND user_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_product_after_sale_item CHECK(order_item_id BETWEEN 1 AND 9007199254740991 AND operation_id BETWEEN 1 AND 9007199254740991
  AND spu_id BETWEEN 1 AND 9007199254740991 AND sku_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_product_after_sale_quantity CHECK(quantity BETWEEN 1 AND 9999),
 CONSTRAINT ck_product_after_sale_movement CHECK(before_stock BETWEEN 0 AND 2147483647 AND after_stock BETWEEN 0 AND 2147483647
  AND CAST(after_stock AS SIGNED)=CAST(before_stock AS SIGNED)+quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'AFTER_SALE_STOCK_RESTORATION_READY' AS result;
