-- DEC-046/065/066: selected upstream order/item fields; stable preparation ids and money allocations.
-- Additive only. No actual orders/payment/member seeds; no changes to existing business rows.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE formal_order_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO formal_order_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('trade_order_prepare','trade_order_prepare_item','trade_cart','trade_cart_user') AND ENGINE='InnoDB')=4
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('trade_order','trade_order_item','trade_order_log'))
 AND NOT EXISTS(SELECT 1 FROM trade_order_prepare WHERE stage='CREATED'),1,0);
CREATE TABLE trade_order (
 id BIGINT NOT NULL,
 tenant_id BIGINT NOT NULL,
 no CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 type TINYINT NOT NULL,
 user_id BIGINT NOT NULL,
 user_remark VARCHAR(500) NOT NULL DEFAULT '',
 status TINYINT NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 product_count INT NOT NULL,
 total_price BIGINT NOT NULL,
 discount_price BIGINT NOT NULL,
 delivery_price BIGINT NOT NULL,
 pay_price BIGINT NOT NULL,
 coupon_id BIGINT NULL,
 pay_order_id BIGINT NULL,
 pay_status BIT(1) NOT NULL DEFAULT b'0',
 pay_time DATETIME(6) NULL,
 pay_deadline DATETIME(6) NOT NULL,
 refund_price BIGINT NOT NULL DEFAULT 0,
 receiver_name VARCHAR(50) NOT NULL,
 receiver_mobile VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 receiver_area_id INT NOT NULL,
 receiver_detail_address VARCHAR(255) NOT NULL,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_order_no(no),
 UNIQUE KEY uk_trade_order_payment(tenant_id,pay_order_id),
 KEY idx_trade_order_user(tenant_id,user_id,id),
 KEY idx_trade_order_deadline(tenant_id,status,pay_deadline,id),
 CONSTRAINT ck_trade_order_identity CHECK(id>0 AND tenant_id>0 AND user_id>0 AND (coupon_id IS NULL OR coupon_id>0) AND (pay_order_id IS NULL OR pay_order_id>0)),
 CONSTRAINT ck_trade_order_no CHECK(no REGEXP '^QH[0-9a-f]{32}$'),
 CONSTRAINT ck_trade_order_status CHECK(type=0 AND status IN(0,10,20,30,40) AND auth_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_trade_order_price CHECK(product_count BETWEEN 1 AND 1999800 AND total_price BETWEEN 0 AND 9007199254740991
  AND discount_price BETWEEN 0 AND total_price AND delivery_price BETWEEN 0 AND 9007199254740991
  AND pay_price BETWEEN 0 AND 9007199254740991 AND pay_price=total_price-discount_price+delivery_price AND refund_price BETWEEN 0 AND pay_price),
 CONSTRAINT ck_trade_order_paid CHECK((pay_status=b'0' AND status IN(0,40) AND pay_time IS NULL)
  OR (pay_status=b'1' AND status IN(10,20,30,40) AND pay_time IS NOT NULL AND (pay_price=0 OR pay_order_id IS NOT NULL))),
 CONSTRAINT ck_trade_order_receiver CHECK(CHAR_LENGTH(TRIM(receiver_name))>0 AND REGEXP_LIKE(receiver_mobile,'^1[3-9][0-9]{9}$','c')
  AND receiver_area_id>0 AND CHAR_LENGTH(TRIM(receiver_detail_address))>0),
 CONSTRAINT ck_trade_order_time CHECK(update_time>=create_time AND pay_deadline=DATE_ADD(create_time,INTERVAL 30 MINUTE) AND (pay_time IS NULL OR pay_time>=create_time))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE trade_order_item (
 id BIGINT NOT NULL,
 tenant_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 cart_id BIGINT NOT NULL,
  spu_id BIGINT NOT NULL,
 spu_name VARCHAR(256) NOT NULL,
 sku_id BIGINT NOT NULL,
 spu_version BIGINT NOT NULL,
 sku_version BIGINT NOT NULL,
 properties JSON NOT NULL,
 pic_url VARCHAR(2048) NULL,
 count INT NOT NULL,
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
 KEY idx_trade_order_item_user(tenant_id,user_id,order_id),
 UNIQUE KEY uk_trade_order_item_sku(tenant_id,order_id,sku_id),
 CONSTRAINT ck_trade_order_item_identity CHECK(tenant_id>0 AND order_id>0 AND user_id>0 AND cart_id>0 AND spu_id>0 AND sku_id>0),
 CONSTRAINT ck_trade_order_item_version CHECK(spu_version>=0 AND sku_version>=0),
 CONSTRAINT ck_trade_order_item_name CHECK(CHAR_LENGTH(TRIM(spu_name))>0),
 CONSTRAINT ck_trade_order_item_properties CHECK(JSON_TYPE(properties)='ARRAY'),
 CONSTRAINT ck_trade_order_item_count CHECK(count BETWEEN 1 AND 9999 AND price BETWEEN 1 AND 2000000000),
 CONSTRAINT ck_trade_order_item_price CHECK(goods_price=CAST(price AS SIGNED)*count
  AND discount_price BETWEEN 0 AND goods_price AND delivery_price BETWEEN 0 AND 9007199254740991
  AND pay_price BETWEEN 0 AND 9007199254740991 AND pay_price=goods_price-discount_price+delivery_price),
 CONSTRAINT ck_trade_order_item_discount CHECK(unit_discount_base>=0 AND unit_discount_remainder BETWEEN 0 AND count-1
  AND CAST(unit_discount_base AS DECIMAL(30,0))*count+unit_discount_remainder=discount_price),
 CONSTRAINT ck_trade_order_item_delivery CHECK(unit_delivery_base>=0 AND unit_delivery_remainder BETWEEN 0 AND count-1
  AND CAST(unit_delivery_base AS DECIMAL(30,0))*count+unit_delivery_remainder=delivery_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE trade_order_log (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 from_status TINYINT NULL,
 to_status TINYINT NOT NULL,
 order_version BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_order_log_version(tenant_id,order_id,order_version),
 CONSTRAINT ck_trade_order_log_identity CHECK(tenant_id>0 AND order_id>0 AND actor_id>0 AND order_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_trade_order_log_transition CHECK(action='CREATE' AND from_status IS NULL AND to_status=0 AND order_version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'FORMAL_ORDER_SCHEMA_CREATED' AS result;
