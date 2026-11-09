-- DEC-046/057/065/066. Fixed trade job enumeration progress, not a generic scheduler or business recovery table.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE trade_tenant_job_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO trade_tenant_job_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('trade_order_prepare','trade_order','trade_order_outbox','trade_order_recovery') AND ENGINE='InnoDB')=4
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_tenant_job_cursor'),1,0);
CREATE TABLE trade_tenant_job_cursor (
 job_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 shard_index INT NOT NULL,
 shard_total INT NOT NULL,
 last_tenant_id BIGINT NOT NULL DEFAULT 0,
 ceiling_tenant_id BIGINT NOT NULL DEFAULT 0,
 lease_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(6) NULL,
 update_time DATETIME(6) NOT NULL,
 PRIMARY KEY(job_code,shard_total,shard_index),
 CONSTRAINT ck_trade_tenant_job_code CHECK(job_code IN('ORDER_RECOVERY','OUTBOX_PUBLISH')),
 CONSTRAINT ck_trade_tenant_job_shard CHECK(shard_total BETWEEN 1 AND 128 AND shard_index>=0 AND shard_index<shard_total),
 CONSTRAINT ck_trade_tenant_job_range CHECK(last_tenant_id BETWEEN 0 AND 9007199254740991 AND ceiling_tenant_id BETWEEN last_tenant_id AND 9007199254740991),
 CONSTRAINT ck_trade_tenant_job_lease CHECK((lease_owner IS NULL AND lease_until IS NULL)
  OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL AND lease_until>update_time)),
 CONSTRAINT ck_trade_tenant_job_owner CHECK(lease_owner IS NULL OR REGEXP_LIKE(lease_owner,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'TRADE_TENANT_JOB_CURSOR_READY' AS result;
