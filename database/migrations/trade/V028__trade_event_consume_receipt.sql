-- DEC-046/057/065/066. Receipt and existing recovery intent share one local transaction.
-- No message delivery, task execution or business data initialization.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE trade_event_receipt_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO trade_event_receipt_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('trade_order_outbox','trade_order_recovery') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_event_receipt'),1,0);
CREATE TABLE trade_order_event_receipt (
 tenant_id BIGINT NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 consumer_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 task_count INT NOT NULL,
 consumed_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,event_id,consumer_code),
 KEY idx_trade_event_receipt_time(tenant_id,consumed_at,event_id),
 CONSTRAINT ck_trade_receipt_tenant CHECK(tenant_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_trade_receipt_event CHECK(REGEXP_LIKE(event_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
 CONSTRAINT ck_trade_receipt_consumer CHECK(consumer_code='TRADE_RECOVERY_V1'),
 CONSTRAINT ck_trade_receipt_hash CHECK(REGEXP_LIKE(payload_sha256,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_trade_receipt_tasks CHECK(task_count BETWEEN 0 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'TRADE_EVENT_RECEIPT_CREATED' AS result;
