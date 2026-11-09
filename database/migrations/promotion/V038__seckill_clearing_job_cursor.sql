-- DEC-046. Reuse POOL_RETURN_RECOVERY; preserve existing positions, leases and business records.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_clearing_cursor_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_clearing_cursor_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor')=9
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor' AND COLUMN_NAME='after_clearing_activity')
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor'
  AND CONSTRAINT_NAME='ck_promo_seckill_expiry_position' AND ENFORCED='YES')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_clearing_pool')=16,1,0);
ALTER TABLE promotion_seckill_cache_cursor
 ADD COLUMN after_clearing_activity BIGINT NULL,
 ADD CONSTRAINT ck_promo_seckill_clearing_position CHECK(after_clearing_activity IS NULL OR after_clearing_activity BETWEEN 1 AND 9007199254740991);
SELECT 'SECKILL_CLEARING_CURSOR_READY';
