-- DEC-046. Original binding metadata and close protocol remain; no order, intent or resource rows initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_binding_cleanup_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_binding_cleanup_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_seckill_order_binding')=25
 AND NOT EXISTS(SELECT 1 FROM trade_seckill_order_binding)
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_tenant_job_cursor')=8
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='trade_tenant_job_cursor' AND CONSTRAINT_NAME='ck_trade_tenant_job_code' AND ENFORCED='YES'),1,0);
ALTER TABLE trade_seckill_order_binding
 ADD COLUMN cleanup_attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN next_cleanup_at DATETIME(3) NOT NULL DEFAULT '1970-01-01 00:00:00.000',
 ADD KEY idx_seckill_binding_cleanup(tenant_id,state,next_cleanup_at,quote_expires_at,request_no),
 ADD CONSTRAINT ck_seckill_binding_cleanup CHECK(cleanup_attempts BETWEEN 0 AND 10);
ALTER TABLE trade_tenant_job_cursor DROP CHECK ck_trade_tenant_job_code,
 ADD CONSTRAINT ck_trade_tenant_job_code CHECK(job_code IN('ORDER_RECOVERY','OUTBOX_PUBLISH','SECKILL_RESULT_PUBLISH','SECKILL_BINDING_CLEANUP'));
SELECT 'SECKILL_BINDING_CLEANUP_READY';
