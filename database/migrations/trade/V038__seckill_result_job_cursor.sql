-- DEC-046. Reuse the existing fixed trade job cursor; no rows or recovery budgets initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_result_job_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_result_job_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_tenant_job_cursor')=8
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='trade_tenant_job_cursor' AND CONSTRAINT_NAME='ck_trade_tenant_job_code' AND ENFORCED='YES'),1,0);
ALTER TABLE trade_tenant_job_cursor DROP CHECK ck_trade_tenant_job_code,
 ADD CONSTRAINT ck_trade_tenant_job_code CHECK(job_code IN('ORDER_RECOVERY','OUTBOX_PUBLISH','SECKILL_RESULT_PUBLISH'));
SELECT 'SECKILL_RESULT_JOB_CURSOR_READY';
