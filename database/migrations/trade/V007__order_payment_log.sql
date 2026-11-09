-- DEC-046/065/066. Compatible expansion: payment binding and zero-price confirmation logs.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE order_payment_log_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO order_payment_log_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_log_transition'),1,0);
ALTER TABLE trade_order_log DROP CHECK ck_trade_order_log_transition,
 ADD CONSTRAINT ck_trade_order_log_transition CHECK(
  (action='CREATE' AND from_status IS NULL AND to_status=0 AND order_version=0)
  OR (action='PAY_BIND' AND from_status=0 AND to_status=0 AND order_version>0)
  OR (action='FREE_PAY' AND from_status=0 AND to_status=10 AND order_version>0));
SELECT 'ORDER_PAYMENT_LOG_EXPANDED' AS result;
