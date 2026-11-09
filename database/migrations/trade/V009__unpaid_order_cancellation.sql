-- DEC-046/065/066. Upstream cancel type/time fields plus explicit cross-service cleanup acknowledgement.
-- Additive metadata only; existing rows retain false/null defaults. No resource/payment business writes.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE unpaid_cancel_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO unpaid_cancel_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME IN('ck_trade_order_paid','ck_trade_order_log_transition'))=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order'
  AND COLUMN_NAME IN('cancel_type','cancel_time','cancel_resources_released'))
 AND NOT EXISTS(SELECT 1 FROM trade_order WHERE status=40),1,0);
ALTER TABLE trade_order ADD COLUMN cancel_type TINYINT NULL AFTER refund_price,
 ADD COLUMN cancel_time DATETIME(6) NULL AFTER cancel_type,
 ADD COLUMN cancel_resources_released BIT(1) NOT NULL DEFAULT b'0' AFTER cancel_time,
 ADD KEY idx_trade_order_cancel_cleanup(tenant_id,status,cancel_resources_released,id),
 ADD CONSTRAINT ck_trade_order_cancel CHECK(
  (status<>40 AND cancel_type IS NULL AND cancel_time IS NULL AND cancel_resources_released=b'0')
  OR (status=40 AND pay_status=b'0' AND cancel_type IS NOT NULL AND cancel_type IN(10,30)
   AND cancel_time IS NOT NULL AND cancel_time>=create_time AND cancel_time<=update_time
   AND (cancel_type<>10 OR cancel_time>=pay_deadline)));
ALTER TABLE trade_order_log DROP CHECK ck_trade_order_log_transition,
 ADD CONSTRAINT ck_trade_order_log_transition CHECK(
  (action='CREATE' AND from_status IS NULL AND to_status=0 AND order_version=0 AND actor_type='MEMBER')
  OR (action='PAY_BIND' AND from_status=0 AND to_status=0 AND order_version>0 AND actor_type='MEMBER')
  OR (action='FREE_PAY' AND from_status=0 AND to_status=10 AND order_version>0 AND actor_type='MEMBER')
  OR (action='PAY_CONFIRM' AND from_status=0 AND to_status=10 AND order_version>0 AND actor_type='SYSTEM')
  OR (action='MEMBER_CANCEL' AND from_status=0 AND to_status=40 AND order_version>0 AND actor_type='MEMBER')
  OR (action='TIMEOUT_CANCEL' AND from_status=0 AND to_status=40 AND order_version>0 AND actor_type='SYSTEM'));
SELECT 'UNPAID_ORDER_CANCELLATION_READY' AS result;
