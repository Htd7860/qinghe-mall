-- DEC-046. Preserve all original recovery positions and pending return numbers.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_pool_preparation_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_pool_preparation_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor')=7
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor' AND COLUMN_NAME='after_pool_id')
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor'
  AND CONSTRAINT_NAME='ck_promo_seckill_return_position' AND ENFORCED='YES'),1,0);
ALTER TABLE promotion_seckill_cache_cursor
 ADD COLUMN after_pool_id BIGINT NULL,
 ADD CONSTRAINT ck_promo_seckill_pool_position CHECK(after_pool_id IS NULL OR after_pool_id BETWEEN 1 AND 9007199254740991);
SELECT 'SECKILL_POOL_PREPARATION_READY';
