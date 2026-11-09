-- DEC-046/057/065/066. Original committed event delivery leases; no event/job/broker is dispatched.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE outbox_publish_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO outbox_publish_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_outbox')=12
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_outbox' AND COLUMN_NAME='lease_owner')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_outbox_type' AND CHECK_CLAUSE LIKE '%AFTERSALE_DECIDED%'),1,0);
ALTER TABLE trade_order_outbox
 ADD COLUMN lease_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN lease_until DATETIME(6) NULL,
 ADD COLUMN last_error_code INT NULL,
 ADD KEY idx_trade_outbox_tenant_due(tenant_id,status,available_at,event_id),
 ADD KEY idx_trade_outbox_tenant_lease(tenant_id,status,lease_until,event_id),
 DROP CHECK ck_trade_order_outbox_status,
 ADD CONSTRAINT ck_trade_order_outbox_status CHECK(
  (status='PENDING' AND attempts BETWEEN 0 AND 9)
  OR (status IN('SENDING','MANUAL') AND attempts BETWEEN 1 AND 10)
  OR (status='PUBLISHED' AND attempts BETWEEN 0 AND 10)),
 DROP CHECK ck_trade_order_outbox_published,
 ADD CONSTRAINT ck_trade_order_outbox_published CHECK(
  (status='PUBLISHED' AND published_at IS NOT NULL AND published_at>=created_at)
  OR (status IN('PENDING','SENDING','MANUAL') AND published_at IS NULL)),
 ADD CONSTRAINT ck_trade_outbox_lease CHECK(
  (status='SENDING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL AND lease_until>created_at)
  OR (status<>'SENDING' AND lease_owner IS NULL AND lease_until IS NULL)),
 ADD CONSTRAINT ck_trade_outbox_error CHECK(last_error_code IS NULL OR last_error_code=503);
SELECT 'OUTBOX_PUBLISH_LEASES_READY' AS result;
