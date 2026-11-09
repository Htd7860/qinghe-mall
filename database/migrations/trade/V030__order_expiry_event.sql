-- DEC-046/057/065/066. Fixed native delayed order-expiry notification; no dispatch or business backfill execution.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE order_expiry_event_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO order_expiry_event_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_outbox')=16
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_outbox_type'
  AND CHECK_CLAUSE LIKE '%AFTERSALE_DECIDED%' AND CHECK_CLAUSE NOT LIKE '%ORDER_EXPIRE_CHECK%')
 AND NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_outbox_expiry_due'),1,0);
ALTER TABLE trade_order_outbox
 DROP CHECK ck_trade_order_outbox_type,
 ADD CONSTRAINT ck_trade_order_outbox_type CHECK(event_type IN('ORDER_PREPARED','ORDER_DELIVERED','ORDER_RECEIVED','AFTERSALE_APPLIED','AFTERSALE_DECIDED','ORDER_EXPIRE_CHECK')),
 ADD CONSTRAINT ck_trade_outbox_expiry_due CHECK(event_type<>'ORDER_EXPIRE_CHECK' OR (
  aggregate_version=0 AND JSON_CONTAINS_PATH(payload,'one','$.dueAt')=1
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.dueAt'))='INTEGER'
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.dueAt')) AS UNSIGNED) BETWEEN 1 AND 9007199254740991));
SELECT 'ORDER_EXPIRY_EVENT_READY' AS result;
