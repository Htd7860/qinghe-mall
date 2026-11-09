-- DEC-046/065, B05/B06/B10. Preparation is not a payable trade_order.
-- Stable preparation/item ids are retained for later formal order/stock/coupon processing.
-- Additive only; no member, cart, order or stock seeds. Do not rerun.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE trade_order_prepare_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO trade_order_prepare_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('trade_cart','trade_cart_user','trade_settlement_quote') AND ENGINE='InnoDB')=3
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('trade_order_prepare','trade_order_prepare_item','trade_order_outbox')),1,0);

CREATE TABLE trade_order_prepare (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 no CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 stage VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 remark VARCHAR(500) NOT NULL DEFAULT '',
 snapshot JSON NOT NULL,
 product_count INT NOT NULL,
 total_price BIGINT NOT NULL,
 discount_price BIGINT NOT NULL,
 delivery_price BIGINT NOT NULL,
 pay_price BIGINT NOT NULL,
 prepare_deadline DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_order_prepare_request(tenant_id,user_id,request_id),
 UNIQUE KEY uk_trade_order_prepare_quote(tenant_id,quote_id),
 UNIQUE KEY uk_trade_order_prepare_no(no),
 KEY idx_trade_order_prepare_stage(tenant_id,stage,prepare_deadline,id),
 CONSTRAINT ck_trade_order_prepare_identity CHECK(tenant_id>0 AND user_id>0),
 CONSTRAINT ck_trade_order_prepare_uuid CHECK(
  request_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
  AND quote_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
 CONSTRAINT ck_trade_order_prepare_hash CHECK(request_hash REGEXP '^[0-9a-f]{64}$'),
 CONSTRAINT ck_trade_order_prepare_no CHECK(no REGEXP '^QH[0-9a-f]{32}$'),
 CONSTRAINT ck_trade_order_prepare_stage CHECK(stage IN('PREPARED','PROCESSING','COMPENSATING','CREATED','FAILED','MANUAL')
  AND auth_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_trade_order_prepare_snapshot CHECK(JSON_TYPE(snapshot)='OBJECT'),
 CONSTRAINT ck_trade_order_prepare_price CHECK(product_count BETWEEN 1 AND 1999800
  AND total_price BETWEEN 0 AND 9007199254740991 AND discount_price BETWEEN 0 AND total_price
  AND delivery_price BETWEEN 0 AND 9007199254740991 AND pay_price BETWEEN 0 AND 9007199254740991
  AND pay_price=total_price-discount_price+delivery_price),
 CONSTRAINT ck_trade_order_prepare_deadline CHECK(prepare_deadline>created_at),
 CONSTRAINT ck_trade_order_prepare_time CHECK(updated_at>=created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE trade_order_prepare_item (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 preparation_id BIGINT NOT NULL,
 cart_id BIGINT NOT NULL,
 cart_line_version BIGINT NOT NULL,
 spu_id BIGINT NOT NULL,
 spu_name VARCHAR(256) NOT NULL,
 sku_id BIGINT NOT NULL,
 spu_version BIGINT NOT NULL,
 sku_version BIGINT NOT NULL,
 properties JSON NOT NULL,
 pic_url VARCHAR(2048) NULL,
 quantity INT NOT NULL,
 price INT NOT NULL,
 goods_price BIGINT NOT NULL,
 discount_price BIGINT NOT NULL,
 delivery_price BIGINT NOT NULL,
 pay_price BIGINT NOT NULL,
 unit_discount_base BIGINT NOT NULL,
 unit_discount_remainder INT NOT NULL,
 unit_delivery_base BIGINT NOT NULL,
 unit_delivery_remainder INT NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_order_prepare_item_cart(tenant_id,preparation_id,cart_id),
 UNIQUE KEY uk_trade_order_prepare_item_sku(tenant_id,preparation_id,sku_id),
 CONSTRAINT ck_trade_order_prepare_item_identity CHECK(tenant_id>0 AND preparation_id>0 AND cart_id>0 AND spu_id>0 AND sku_id>0),
 CONSTRAINT ck_trade_order_prepare_item_version CHECK(cart_line_version>=0 AND spu_version>=0 AND sku_version>=0),
 CONSTRAINT ck_trade_order_prepare_item_name CHECK(CHAR_LENGTH(TRIM(spu_name))>0),
 CONSTRAINT ck_trade_order_prepare_item_properties CHECK(JSON_TYPE(properties)='ARRAY'),
 CONSTRAINT ck_trade_order_prepare_item_quantity CHECK(quantity BETWEEN 1 AND 9999 AND price BETWEEN 1 AND 2000000000),
 CONSTRAINT ck_trade_order_prepare_item_price CHECK(goods_price=CAST(price AS SIGNED)*quantity
  AND discount_price BETWEEN 0 AND goods_price AND delivery_price BETWEEN 0 AND 9007199254740991
  AND pay_price BETWEEN 0 AND 9007199254740991 AND pay_price=goods_price-discount_price+delivery_price),
 CONSTRAINT ck_trade_order_prepare_item_discount CHECK(unit_discount_base>=0 AND unit_discount_remainder BETWEEN 0 AND quantity-1
  AND CAST(unit_discount_base AS DECIMAL(30,0))*quantity+unit_discount_remainder=discount_price),
 CONSTRAINT ck_trade_order_prepare_item_delivery CHECK(unit_delivery_base>=0 AND unit_delivery_remainder BETWEEN 0 AND quantity-1
  AND CAST(unit_delivery_base AS DECIMAL(30,0))*quantity+unit_delivery_remainder=delivery_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE trade_order_outbox (
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 tenant_id BIGINT NOT NULL,
 preparation_id BIGINT NOT NULL,
 event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 aggregate_version BIGINT NOT NULL,
 schema_version INT NOT NULL,
 payload JSON NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 available_at DATETIME(6) NOT NULL,
 published_at DATETIME(6) NULL,
 PRIMARY KEY(event_id),
 UNIQUE KEY uk_trade_order_outbox_business(tenant_id,preparation_id,event_type,aggregate_version),
 KEY idx_trade_order_outbox_pending(status,available_at,event_id),
 CONSTRAINT ck_trade_order_outbox_identity CHECK(tenant_id>0 AND preparation_id>0),
 CONSTRAINT ck_trade_order_outbox_event CHECK(event_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
 CONSTRAINT ck_trade_order_outbox_version CHECK(aggregate_version>=0 AND schema_version=1 AND attempts BETWEEN 0 AND 10),
 CONSTRAINT ck_trade_order_outbox_type CHECK(event_type='ORDER_PREPARED'),
 CONSTRAINT ck_trade_order_outbox_payload CHECK(JSON_TYPE(payload)='OBJECT'),
 CONSTRAINT ck_trade_order_outbox_status CHECK(status IN('PENDING','PUBLISHED','MANUAL')),
 CONSTRAINT ck_trade_order_outbox_published CHECK(
  (status='PUBLISHED' AND published_at IS NOT NULL AND published_at>=created_at)
  OR (status IN('PENDING','MANUAL') AND published_at IS NULL)),
 CONSTRAINT ck_trade_order_outbox_time CHECK(available_at>=created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'TRADE_ORDER_PREPARATION_CREATED';
