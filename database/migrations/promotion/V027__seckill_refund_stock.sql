-- DEC-046. Add refund-stock progress, preserve original facts and quota counters.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_refund_stock_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_refund_stock_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_refund_record')=23
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_refund_record)
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_result_entry_type' AND CHECK_CLAUSE NOT LIKE '%ORDER_REFUND_RESTORED%'),1,0);
ALTER TABLE promotion_seckill_refund_record
 ADD COLUMN restored_total INT NULL,
 ADD COLUMN restored_time DATETIME(3) NULL,
 ADD COLUMN cache_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_REQUIRED',
 ADD KEY idx_seckill_refund_cache(tenant_id,cache_state,after_sale_id),
 ADD CONSTRAINT ck_seckill_refund_stock_progress CHECK(
  (state IN('PENDING','NOT_REQUIRED') AND restored_total IS NULL AND restored_time IS NULL AND cache_state='NOT_REQUIRED')
  OR (state='RESTORED' AND restored_total IS NOT NULL AND restored_total BETWEEN restore_count AND 9999
   AND restored_time IS NOT NULL AND cache_state IN('PENDING','SYNCED')));
ALTER TABLE promotion_seckill_quota_entry DROP CHECK ck_seckill_result_entry_type,
 ADD CONSTRAINT ck_seckill_result_entry_type CHECK(event_type IN('ALLOCATE','COMPENSATE','ACCEPT','ORDER_CREATED','CREATE_CLOSED','ORDER_PAID','ORDER_CANCELLED','ORDER_REFUND_RESTORED'));
SELECT 'SECKILL_REFUND_STOCK_READY';
