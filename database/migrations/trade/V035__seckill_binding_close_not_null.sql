-- Strengthen nullable close-code CHECK: SQL UNKNOWN must not allow a CLOSED row without its terminal code.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_binding_close_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_binding_close_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_seckill_order_binding')=25
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='trade_seckill_order_binding' AND CONSTRAINT_NAME='ck_trade_seckill_binding_close' AND ENFORCED='YES')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='trade_seckill_order_binding' AND CONSTRAINT_NAME='ck_trade_seckill_binding_close_complete'),1,0);
ALTER TABLE trade_seckill_order_binding ADD CONSTRAINT ck_trade_seckill_binding_close_complete CHECK(
 state<>20 OR (close_code IS NOT NULL AND close_code='CANCELLED_BEFORE_ORDER'));
SELECT 'SECKILL_BINDING_CLOSED_CODE_REQUIRED' AS result;
