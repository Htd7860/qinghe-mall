-- DEC-046. Reuse original cumulative targets; historical refunds never reserve a new activity pool.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_historical_return_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_historical_return_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_pool_return')=21
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_pool_return' AND COLUMN_NAME='source_kind')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_clearing_pool')=16,1,0);
ALTER TABLE promotion_seckill_pool_return
 ADD COLUMN source_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'POOL',
 ADD COLUMN after_sale_id BIGINT NULL,
 ADD UNIQUE KEY uk_seckill_return_sale(tenant_id,after_sale_id),
 ADD CONSTRAINT ck_seckill_return_source CHECK((source_kind='POOL' AND after_sale_id IS NULL)
  OR (source_kind='HISTORICAL_REFUND' AND after_sale_id IS NOT NULL AND after_sale_id BETWEEN 1 AND 9007199254740991));
SELECT 'SECKILL_HISTORICAL_REFUND_PLAN_READY';
