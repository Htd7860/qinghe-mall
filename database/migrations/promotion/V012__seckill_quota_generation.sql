-- DEC-046/057/065/066. Explicit generation separates repeated publishing ledgers.
-- Refuse to invent a generation for existing business entries; current local table must be empty.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_quota_generation_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_quota_generation_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_quota_entry')=17
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_quota_entry)
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_quota_entry' AND COLUMN_NAME='generation'),1,0);
ALTER TABLE promotion_seckill_quota_entry
 ADD COLUMN generation INT UNSIGNED NOT NULL COMMENT '原发布代次，不因恢复草稿覆盖',
 ADD KEY idx_quota_generation(tenant_id,activity_id,generation,sku_id,id),
 ADD CONSTRAINT ck_seckill_quota_generation CHECK(generation BETWEEN 1 AND 2147483647);
SELECT 'SECKILL_QUOTA_GENERATION_READY' AS result;
