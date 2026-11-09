-- DEC-046. One committed trade result receipt; pending Redis release remains durable.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_result_receipt_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_result_receipt_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_order_result')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_request')=34
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_member_quota')=9
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_quota_entry')=18
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_quota_entry' AND CONSTRAINT_NAME='ck_seckill_result_entry_type' AND ENFORCED='YES'),1,0);

CREATE TABLE promotion_seckill_order_result (
 tenant_id BIGINT NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 aggregate_version BIGINT NOT NULL,
 event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_state TINYINT NOT NULL,
 order_id BIGINT NULL,
 request_version BIGINT NOT NULL,
 cache_sync_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 cache_synced_at DATETIME(3) NULL,
 processed_at DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,event_id),
 KEY idx_seckill_result_request(tenant_id,request_no,event_id),
 KEY idx_seckill_result_cache(tenant_id,cache_sync_state,processed_at,event_id),
 CONSTRAINT ck_seckill_order_result_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991
  AND aggregate_version BETWEEN 1 AND 9007199254740991 AND request_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_seckill_order_result_keys CHECK(
  REGEXP_LIKE(event_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(payload_hash,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_seckill_order_result_outcome CHECK(
  (event_type='SECKILL_ORDER_CREATED' AND result_state=10 AND order_id IS NOT NULL AND order_id BETWEEN 1 AND 9007199254740991
    AND cache_sync_state='NOT_REQUIRED' AND cache_synced_at IS NULL)
  OR (event_type='SECKILL_REQUEST_CLOSED' AND result_state=20 AND order_id IS NULL
    AND ((cache_sync_state='PENDING' AND cache_synced_at IS NULL) OR (cache_sync_state='SYNCED' AND cache_synced_at IS NOT NULL))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='verified trade result receipt plus durable cache release backlog, no address';

ALTER TABLE promotion_seckill_quota_entry
 DROP CHECK ck_seckill_result_entry_type,
 ADD CONSTRAINT ck_seckill_result_entry_type CHECK(event_type IN('ALLOCATE','COMPENSATE','ACCEPT','ORDER_CREATED','CREATE_CLOSED'));
SELECT 'SECKILL_ORDER_RESULT_RECEIPTS_READY';
