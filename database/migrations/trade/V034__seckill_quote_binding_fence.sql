-- DEC-046/057/065/066. Evolves the unapplied historical V002 order-binding subset. Never replay V002.
-- No quote, binding, order, admission, event or service credential is initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_binding_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_binding_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_seckill_order_binding')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_seckill_quote')=22
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='trade_seckill_quote' AND CONSTRAINT_NAME='ck_trade_seckill_quote_binding' AND ENFORCED='YES'),1,0);

CREATE TABLE trade_seckill_order_binding (
 tenant_id BIGINT NOT NULL,
 request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 member_id BIGINT NOT NULL,
 client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quote_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 activity_id BIGINT NULL,
 generation INT NULL,
 activity_version BIGINT NULL,
 redis_epoch BIGINT NULL,
 sku_id BIGINT NULL,
 quantity INT NULL,
 price_cent INT NULL,
 goods_amount BIGINT NULL,
 shipping_amount BIGINT NULL,
 pay_amount BIGINT NULL,
 quote_expires_at DATETIME(3) NULL,
 order_id BIGINT NULL,
 state TINYINT NOT NULL COMMENT '0 quote bound / 10 order created / 20 permanent closed / 30 in-transaction unique-key guard',
 fence_version BIGINT NOT NULL DEFAULT 0,
 close_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 closed_at DATETIME(3) NULL,
 PRIMARY KEY(tenant_id,request_no),
 UNIQUE KEY uk_seckill_binding_client(tenant_id,member_id,client_request_id),
 UNIQUE KEY uk_seckill_binding_quote(tenant_id,quote_id),
 UNIQUE KEY uk_seckill_binding_order(tenant_id,order_id),
 KEY idx_seckill_binding_member(tenant_id,member_id,created_at),
 CONSTRAINT ck_trade_seckill_binding_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND member_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_trade_seckill_binding_refs CHECK(REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(client_request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(quote_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_digest,'^[0-9a-f]{64}$','c') AND REGEXP_LIKE(quote_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_trade_seckill_binding_state CHECK(state IN(0,10,20,30) AND fence_version BETWEEN 0 AND 9007199254740991
  AND ((state=10 AND order_id IS NOT NULL AND order_id BETWEEN 1 AND 9007199254740991) OR (state<>10 AND order_id IS NULL))),
 CONSTRAINT ck_trade_seckill_binding_close CHECK((state=20 AND closed_at IS NOT NULL AND close_code='CANCELLED_BEFORE_ORDER' AND fence_version>0)
  OR (state<>20 AND closed_at IS NULL AND close_code IS NULL)),
 CONSTRAINT ck_trade_seckill_binding_fact_group CHECK(
  (state IN(20,30) AND activity_id IS NULL AND generation IS NULL AND activity_version IS NULL AND redis_epoch IS NULL
    AND sku_id IS NULL AND quantity IS NULL AND price_cent IS NULL AND goods_amount IS NULL AND shipping_amount IS NULL AND pay_amount IS NULL AND quote_expires_at IS NULL)
  OR (activity_id IS NOT NULL AND generation IS NOT NULL AND activity_version IS NOT NULL AND redis_epoch IS NOT NULL
    AND sku_id IS NOT NULL AND quantity IS NOT NULL AND price_cent IS NOT NULL AND goods_amount IS NOT NULL AND shipping_amount IS NOT NULL AND pay_amount IS NOT NULL AND quote_expires_at IS NOT NULL)),
 CONSTRAINT ck_trade_seckill_binding_facts CHECK(activity_id IS NULL OR (activity_id BETWEEN 1 AND 9007199254740991 AND generation>0
  AND activity_version BETWEEN 0 AND 9007199254740991 AND redis_epoch=1 AND sku_id BETWEEN 1 AND 9007199254740991
  AND quantity BETWEEN 1 AND 9999 AND price_cent BETWEEN 1 AND 2000000000 AND goods_amount=CAST(price_cent AS SIGNED)*quantity
  AND shipping_amount>=0 AND pay_amount=goods_amount+shipping_amount AND pay_amount<=9007199254740991))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='original quote claim and create/close fence; closed first cannot reopen';
SELECT 'SECKILL_QUOTE_BINDING_FENCE_SCHEMA_READY' AS result;
