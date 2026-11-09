-- DEC-046. Extend the existing shared order-ID sequence; never fabricate a cart ID.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_order_source_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_order_source_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_prepare')=19
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_prepare_item')=22
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_item')=24
 AND NOT EXISTS(SELECT 1 FROM trade_order WHERE type<>0)
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('trade_order_prepare','trade_order_prepare_item','trade_order_item') AND COLUMN_NAME='source_type'),1,0);

ALTER TABLE trade_order_prepare
 ADD COLUMN source_type TINYINT NOT NULL DEFAULT 0 COMMENT '0 ordinary / 1 seckill shared ID reservation',
 DROP INDEX uk_trade_order_prepare_request,
 ADD UNIQUE KEY uk_trade_order_prepare_request(tenant_id,user_id,source_type,request_id),
 DROP INDEX uk_trade_order_prepare_quote,
 ADD UNIQUE KEY uk_trade_order_prepare_quote(tenant_id,source_type,quote_id),
 ADD CONSTRAINT ck_trade_prepare_source CHECK(source_type IN(0,1)
  AND (source_type=0 OR (stage='CREATED' AND discount_price=0)));

ALTER TABLE trade_order_prepare_item
 ADD COLUMN source_type TINYINT NOT NULL DEFAULT 0,
 MODIFY COLUMN cart_id BIGINT NULL,
 MODIFY COLUMN cart_line_version BIGINT NULL,
 DROP CHECK ck_trade_order_prepare_item_identity,
 DROP CHECK ck_trade_order_prepare_item_version,
 ADD CONSTRAINT ck_trade_order_prepare_item_identity CHECK(tenant_id>0 AND preparation_id>0 AND spu_id>0 AND sku_id>0),
 ADD CONSTRAINT ck_trade_order_prepare_item_version CHECK(spu_version>=0 AND sku_version>=0),
 ADD CONSTRAINT ck_trade_prepare_item_source CHECK(
  (source_type=0 AND cart_id IS NOT NULL AND cart_id>0 AND cart_line_version IS NOT NULL AND cart_line_version>=0)
  OR (source_type=1 AND cart_id IS NULL AND cart_line_version IS NULL AND discount_price=0));

ALTER TABLE trade_order_item
 ADD COLUMN source_type TINYINT NOT NULL DEFAULT 0,
 MODIFY COLUMN cart_id BIGINT NULL,
 DROP CHECK ck_trade_order_item_identity,
 ADD CONSTRAINT ck_trade_order_item_identity CHECK(tenant_id>0 AND order_id>0 AND user_id>0 AND spu_id>0 AND sku_id>0),
 ADD CONSTRAINT ck_trade_order_item_source CHECK(
  (source_type=0 AND cart_id IS NOT NULL AND cart_id>0)
  OR (source_type=1 AND cart_id IS NULL AND discount_price=0));

ALTER TABLE trade_order
 DROP CHECK ck_trade_order_status,
 ADD CONSTRAINT ck_trade_order_status CHECK(type IN(0,1) AND status IN(0,10,20,30,40) AND auth_version BETWEEN 0 AND 9007199254740991),
 ADD CONSTRAINT ck_trade_order_seckill CHECK(type<>1 OR (coupon_id IS NULL AND discount_price=0));
SELECT 'SECKILL_SHARED_ORDER_SOURCE_READY';
