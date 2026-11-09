-- DEC-046. Metadata only; do not reset request phase/version or resource terminal/retry history.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_order_reconcile_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_order_reconcile_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_request')=39
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_request)
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_job_cursor' AND CONSTRAINT_NAME='ck_promo_seckill_job_code' AND ENFORCED='YES'),1,0);
ALTER TABLE promotion_seckill_request
 ADD COLUMN resource_check_attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN next_resource_check_at DATETIME(3) NOT NULL DEFAULT '1970-01-01 00:00:00.000',
 ADD KEY idx_seckill_resource_check(tenant_id,resource_state,next_resource_check_at,request_no),
 ADD CONSTRAINT ck_seckill_resource_check_attempts CHECK(resource_check_attempts BETWEEN 0 AND 10);
ALTER TABLE promotion_seckill_job_cursor DROP CHECK ck_promo_seckill_job_code,
 ADD CONSTRAINT ck_promo_seckill_job_code CHECK(job_code IN('SECKILL_COMMAND_PUBLISH','CLOSED_CACHE_SYNC','REQUEST_RECONCILE','INTENT_RECOVERY','ORDER_CACHE_SYNC','ORDER_RESOURCE_SYNC'));
SELECT 'SECKILL_ORDER_RECONCILE_READY';
