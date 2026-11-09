-- DEC-046/065/066. Compatible expansion of the existing order outbox.
-- preparation_id is the stable formal order id; retain the existing business unique key.
-- No business events are inserted by this migration. Do not replay after successful application.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE fulfillment_event_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO fulfillment_event_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_outbox')=12
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_trade_order_outbox_type' AND CHECK_CLAUSE LIKE '%ORDER_PREPARED%'
  AND CHECK_CLAUSE NOT LIKE '%ORDER_DELIVERED%' AND CHECK_CLAUSE NOT LIKE '%ORDER_RECEIVED%')
 AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_outbox' AND INDEX_NAME='uk_trade_order_outbox_business' AND NON_UNIQUE=0)
  ='tenant_id,preparation_id,event_type,aggregate_version'
 AND NOT EXISTS(SELECT 1 FROM trade_order_outbox WHERE event_type<>'ORDER_PREPARED'),1,0);
ALTER TABLE trade_order_outbox
 DROP CHECK ck_trade_order_outbox_type,
 ADD CONSTRAINT ck_trade_order_outbox_type CHECK(event_type IN('ORDER_PREPARED','ORDER_DELIVERED','ORDER_RECEIVED'));
SELECT 'ORDER_FULFILLMENT_EVENT_TYPES_EXPANDED' AS result;
