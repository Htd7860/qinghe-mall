-- DEC-046/065/066. Verified pay-ledger reconciliation; preserve existing member logs/data.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE paid_log_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO paid_log_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME IN('ck_trade_order_log_transition','ck_trade_order_log_identity','ck_trade_order_time'))=3
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_log' AND COLUMN_NAME='actor_type'),1,0);
ALTER TABLE trade_order_log ADD COLUMN actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MEMBER' AFTER actor_id,
 DROP CHECK ck_trade_order_log_identity,
 ADD CONSTRAINT ck_trade_order_log_identity CHECK(tenant_id>0 AND order_id>0 AND order_version BETWEEN 0 AND 9007199254740991
  AND ((actor_type='MEMBER' AND actor_id>0) OR (actor_type='SYSTEM' AND actor_id=0))),
 DROP CHECK ck_trade_order_log_transition,
 ADD CONSTRAINT ck_trade_order_log_transition CHECK(
  (action='CREATE' AND from_status IS NULL AND to_status=0 AND order_version=0 AND actor_type='MEMBER')
  OR (action='PAY_BIND' AND from_status=0 AND to_status=0 AND order_version>0 AND actor_type='MEMBER')
  OR (action='FREE_PAY' AND from_status=0 AND to_status=10 AND order_version>0 AND actor_type='MEMBER')
  OR (action='PAY_CONFIRM' AND from_status=0 AND to_status=10 AND order_version>0 AND actor_type='SYSTEM'));
ALTER TABLE trade_order DROP CHECK ck_trade_order_time,
 ADD CONSTRAINT ck_trade_order_time CHECK(update_time>=create_time AND pay_deadline=DATE_ADD(create_time,INTERVAL 30 MINUTE)
  AND (pay_time IS NULL OR pay_time>=create_time-INTERVAL 1 SECOND));
SELECT 'PAYMENT_CONFIRMATION_LOG_READY' AS result;
