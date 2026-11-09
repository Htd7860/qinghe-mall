-- DEC-046. Reuse existing tenant/cache cursors; no task, tenant, lease or business record initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_order_cache_job_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_order_cache_job_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor')=5
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_job_cursor')=8
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_request')=39
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_job_cursor' AND CONSTRAINT_NAME='ck_promo_seckill_job_code' AND ENFORCED='YES'),1,0);
ALTER TABLE promotion_seckill_cache_cursor
 ADD COLUMN after_request CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD CONSTRAINT ck_promo_seckill_order_cache_position CHECK(after_request IS NULL OR REGEXP_LIKE(after_request,
  '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c'));
ALTER TABLE promotion_seckill_job_cursor DROP CHECK ck_promo_seckill_job_code,
 ADD CONSTRAINT ck_promo_seckill_job_code CHECK(job_code IN('SECKILL_COMMAND_PUBLISH','CLOSED_CACHE_SYNC','REQUEST_RECONCILE','INTENT_RECOVERY','ORDER_CACHE_SYNC'));
SELECT 'SECKILL_ORDER_CACHE_JOB_READY';
