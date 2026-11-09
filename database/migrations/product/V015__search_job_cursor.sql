-- DEC-046: fixed search task rotation only; not a general scheduler/lease framework.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_search_job_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_search_job_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_search_round')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_search_job_cursor'),1,0);
CREATE TABLE product_search_job_cursor (
 shard_index INT NOT NULL,
 shard_total INT NOT NULL,
 last_tenant_id BIGINT NOT NULL DEFAULT 0,
 ceiling_tenant_id BIGINT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0,
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(shard_index,shard_total),
 CONSTRAINT ck_search_job_shard CHECK(shard_total BETWEEN 1 AND 128 AND shard_index BETWEEN 0 AND shard_total-1),
 CONSTRAINT ck_search_job_cursor CHECK(last_tenant_id BETWEEN 0 AND ceiling_tenant_id AND ceiling_tenant_id BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_search_job_version CHECK(version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='固定搜索任务分片轮转；CAS冲突重读，原业务轮次独立恢复';
SELECT 'PRODUCT_SEARCH_JOB_CURSOR_READY';
