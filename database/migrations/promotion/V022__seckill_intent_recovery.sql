-- DEC-046. Current development table is empty; do not invent expiry/progress for historical business intents.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_intent_recovery_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_intent_recovery_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_admission_intent')=15
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_admission_intent)
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_job_cursor')=8,1,0);
ALTER TABLE promotion_seckill_admission_intent
 ADD COLUMN quote_expires_at DATETIME(3) NOT NULL,
 ADD COLUMN cache_sync_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
 ADD COLUMN cache_synced_at DATETIME(3) NULL,
 ADD COLUMN recovery_attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN next_recovery_at DATETIME(3) NOT NULL,
 ADD COLUMN recovery_error_code INT NULL,
 ADD KEY idx_seckill_intent_recovery(tenant_id,cache_sync_state,next_recovery_at,request_no),
 ADD CONSTRAINT ck_seckill_intent_recovery CHECK(recovery_attempts BETWEEN 0 AND 10
  AND (recovery_error_code IS NULL OR recovery_error_code IN(409,503))
  AND ((cache_sync_state='PENDING' AND cache_synced_at IS NULL)
    OR (cache_sync_state='SYNCED' AND cache_synced_at IS NOT NULL AND status IN(10,20))));
ALTER TABLE promotion_seckill_job_cursor DROP CHECK ck_promo_seckill_job_code,
 ADD CONSTRAINT ck_promo_seckill_job_code CHECK(job_code IN('SECKILL_COMMAND_PUBLISH','CLOSED_CACHE_SYNC','REQUEST_RECONCILE','INTENT_RECOVERY'));
SELECT 'SECKILL_INTENT_RECOVERY_READY';
