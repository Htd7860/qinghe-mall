-- DEC-046/065/066. Only four concrete ordinary-order recovery actions; no executable payload/generic workflow.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE order_recovery_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO order_recovery_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('trade_order','trade_order_prepare') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery'),1,0);
CREATE TABLE trade_order_recovery (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 available_at DATETIME(6) NOT NULL,
 lease_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(6) NULL,
 last_error_code INT NULL,
 last_job_id BIGINT NULL,
 trace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_recovery_action(tenant_id,kind,order_id),
 KEY idx_trade_recovery_due(tenant_id,status,available_at,id),
 KEY idx_trade_recovery_lease(tenant_id,status,lease_until,id),
 CONSTRAINT ck_trade_recovery_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND order_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_trade_recovery_kind CHECK(kind IN('PAY_SYNC','CANCEL_EXPIRED','CANCEL_RELEASE','CREATE_COMPENSATE')),
 CONSTRAINT ck_trade_recovery_state CHECK((status='READY' AND attempts BETWEEN 0 AND 9)
  OR (status IN('RUNNING','DONE','MANUAL') AND attempts BETWEEN 1 AND 10)),
 CONSTRAINT ck_trade_recovery_lease CHECK((status='RUNNING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL AND lease_until>update_time)
  OR (status<>'RUNNING' AND lease_owner IS NULL AND lease_until IS NULL)),
 CONSTRAINT ck_trade_recovery_error CHECK(last_error_code IS NULL OR last_error_code IN(400,401,403,404,409,429,500,503)),
 CONSTRAINT ck_trade_recovery_version CHECK(auth_version BETWEEN 0 AND 9007199254740991 AND (last_job_id IS NULL OR last_job_id>0)),
 CONSTRAINT ck_trade_recovery_time CHECK(update_time>=create_time AND available_at>=create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'ORDER_RECOVERY_RECORDS_CREATED' AS result;
