-- DEC-046. Fixed source-derived jobs only; no executor, lease, event or tenant is initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_job_cursor_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_job_cursor_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('promotion_seckill_job_cursor','promotion_seckill_cache_cursor'))
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_order_result')=12
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_outbox')=16,1,0);

CREATE TABLE promotion_seckill_job_cursor (
 job_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 shard_index INT NOT NULL,
 shard_total INT NOT NULL,
 last_tenant_id BIGINT NOT NULL DEFAULT 0,
 ceiling_tenant_id BIGINT NOT NULL DEFAULT 0,
 lease_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(3) NULL,
 update_time DATETIME(3) NOT NULL,
 PRIMARY KEY(job_code,shard_total,shard_index),
 CONSTRAINT ck_promo_seckill_job_code CHECK(job_code IN('SECKILL_COMMAND_PUBLISH','CLOSED_CACHE_SYNC')),
 CONSTRAINT ck_promo_seckill_job_shard CHECK(shard_total BETWEEN 1 AND 128 AND shard_index>=0 AND shard_index<shard_total),
 CONSTRAINT ck_promo_seckill_job_range CHECK(last_tenant_id BETWEEN 0 AND 9007199254740991 AND ceiling_tenant_id BETWEEN last_tenant_id AND 9007199254740991),
 CONSTRAINT ck_promo_seckill_job_lease CHECK((lease_owner IS NULL AND lease_until IS NULL)
  OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL AND lease_until>update_time)),
 CONSTRAINT ck_promo_seckill_job_owner CHECK(lease_owner IS NULL OR REGEXP_LIKE(lease_owner,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='fixed command-publish/cache-sync tenant traversal progress in UTC';

CREATE TABLE promotion_seckill_cache_cursor (
 tenant_id BIGINT NOT NULL,
 after_time DATETIME(3) NULL,
 after_event CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 version BIGINT NOT NULL DEFAULT 0,
 update_time DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id),
 CONSTRAINT ck_promo_seckill_cache_cursor_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_promo_seckill_cache_cursor_position CHECK((after_time IS NULL AND after_event IS NULL)
  OR (after_time IS NOT NULL AND after_event IS NOT NULL AND after_time>='2000-01-01' AND after_time<'2100-01-01'
    AND REGEXP_LIKE(after_event,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='per-tenant closed cache keyset position, CAS prevents stale progress overwrite';
SELECT 'SECKILL_JOB_CURSORS_READY';
