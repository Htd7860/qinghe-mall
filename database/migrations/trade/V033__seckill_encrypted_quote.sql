-- DEC-046/057/065/066. Additive quote storage only; no order/admission/payment or plaintext address initialization.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_quote_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_quote_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_seckill_quote')
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('trade_cart_user','trade_settlement_quote','trade_order') AND ENGINE='InnoDB')=3,1,0);

CREATE TABLE trade_seckill_quote (
 tenant_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 activity_version BIGINT NOT NULL,
 redis_epoch BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 price_cent INT NOT NULL,
 goods_amount BIGINT NOT NULL,
 shipping_amount BIGINT NOT NULL,
 pay_amount BIGINT NOT NULL,
 quote_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 snapshot_cipher MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'AES-256-GCM nonce+ciphertext+tag Base64; never plaintext JSON',
 key_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(3) NOT NULL COMMENT 'UTC original request time; never renewed',
 expires_at DATETIME(3) NOT NULL COMMENT 'min(original time+120sec, original activity end)',
 bound_request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'future original seckill acceptance binding; not client-editable',
 bound_order_id BIGINT NULL,
 PRIMARY KEY(tenant_id,quote_id),
 UNIQUE KEY uk_seckill_quote_member_request(tenant_id,member_id,request_id),
 UNIQUE KEY uk_seckill_quote_binding(tenant_id,bound_request_no),
 UNIQUE KEY uk_seckill_quote_order(tenant_id,bound_order_id),
 KEY idx_seckill_quote_member_active(tenant_id,member_id,bound_request_no,expires_at),
 CONSTRAINT ck_trade_seckill_quote_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND member_id BETWEEN 1 AND 9007199254740991
  AND activity_id BETWEEN 1 AND 9007199254740991 AND sku_id BETWEEN 1 AND 9007199254740991 AND generation>0),
 CONSTRAINT ck_trade_seckill_quote_keys CHECK(REGEXP_LIKE(quote_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_digest,'^[0-9a-f]{64}$','c') AND REGEXP_LIKE(quote_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_trade_seckill_quote_money CHECK(quantity BETWEEN 1 AND 9999 AND price_cent BETWEEN 1 AND 2000000000
  AND goods_amount=CAST(price_cent AS SIGNED)*quantity AND shipping_amount>=0 AND pay_amount=goods_amount+shipping_amount AND pay_amount<=9007199254740991),
 CONSTRAINT ck_trade_seckill_quote_version CHECK(activity_version BETWEEN 0 AND 9007199254740991 AND redis_epoch=1),
 CONSTRAINT ck_trade_seckill_quote_snapshot CHECK(REGEXP_LIKE(key_id,'^[A-Za-z0-9_-]{1,32}$','c')
  AND (snapshot_cipher IS NULL OR OCTET_LENGTH(snapshot_cipher) BETWEEN 40 AND 90000)),
 CONSTRAINT ck_trade_seckill_quote_time CHECK(expires_at>created_at AND expires_at<=TIMESTAMPADD(SECOND,120,created_at)),
 CONSTRAINT ck_trade_seckill_quote_binding CHECK((bound_request_no IS NULL OR REGEXP_LIKE(bound_request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c'))
  AND (bound_order_id IS NULL OR (bound_request_no IS NOT NULL AND bound_order_id BETWEEN 1 AND 9007199254740991)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='single-SKU seckill quote; encrypted private snapshot; immutable original request';
SELECT 'SECKILL_ENCRYPTED_QUOTE_SCHEMA_READY' AS result;
