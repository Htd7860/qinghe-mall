-- DEC-046. Extend the original immutable quota journal vocabulary; no refund or stock DML.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_historical_confirm_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_historical_confirm_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_pool_return')=23
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_result_entry_type' AND CHECK_CLAUSE LIKE '%POOL_RETURNED%' AND CHECK_CLAUSE NOT LIKE '%ORDER_REFUND_RETURNED%'),1,0);
ALTER TABLE promotion_seckill_quota_entry DROP CHECK ck_seckill_result_entry_type,
 ADD CONSTRAINT ck_seckill_result_entry_type CHECK(event_type IN('ALLOCATE','COMPENSATE','ACCEPT','ORDER_CREATED','CREATE_CLOSED','ORDER_PAID','ORDER_CANCELLED',
  'ORDER_REFUND_RESTORED','POOL_RETURNED','ORDER_REFUND_RETURNED'));
SELECT 'SECKILL_HISTORICAL_REFUND_CONFIRM_READY';
