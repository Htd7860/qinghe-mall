-- DEC-046. Expand only the fixed job-code whitelist; no request/job/budget is initialized or reset.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_request_reconcile_job_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_request_reconcile_job_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_job_cursor')=8
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_job_cursor' AND CONSTRAINT_NAME='ck_promo_seckill_job_code' AND ENFORCED='YES')
 AND EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_request'
  AND INDEX_NAME='idx_seckill_request_reconcile'),1,0);
ALTER TABLE promotion_seckill_job_cursor DROP CHECK ck_promo_seckill_job_code,
 ADD CONSTRAINT ck_promo_seckill_job_code CHECK(job_code IN('SECKILL_COMMAND_PUBLISH','CLOSED_CACHE_SYNC','REQUEST_RECONCILE'));
SELECT 'SECKILL_REQUEST_RECONCILE_JOB_READY';
