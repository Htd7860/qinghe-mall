-- DEC-046. Empty development request ledger; preserve request phase separately from terminal resource state.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_order_resources_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_order_resources_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_request')=34
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_request)
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_result_entry_type' AND CHECK_CLAUSE NOT LIKE '%ORDER_PAID%'),1,0);
ALTER TABLE promotion_seckill_request
 ADD COLUMN resource_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'HELD',
 ADD COLUMN resource_order_version BIGINT NULL,
 ADD COLUMN resource_pay_order_id BIGINT NULL,
 ADD COLUMN resource_time DATETIME(3) NULL,
 ADD COLUMN resource_cache_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_REQUIRED',
 ADD KEY idx_seckill_resource_cache(tenant_id,resource_cache_state,request_no),
 ADD CONSTRAINT ck_seckill_request_resource CHECK(
  (resource_state='HELD' AND resource_order_version IS NULL AND resource_pay_order_id IS NULL AND resource_time IS NULL AND resource_cache_state='NOT_REQUIRED')
  OR (resource_state IN('PAID','CANCELLED') AND order_id IS NOT NULL AND resource_order_version IS NOT NULL
   AND resource_order_version BETWEEN 0 AND 9007199254740991 AND resource_pay_order_id IS NOT NULL
   AND resource_pay_order_id BETWEEN 1 AND 9007199254740991 AND resource_time IS NOT NULL
   AND resource_cache_state IN('PENDING','SYNCED') AND (resource_state<>'CANCELLED' OR status=50)));
ALTER TABLE promotion_seckill_quota_entry DROP CHECK ck_seckill_result_entry_type,
 ADD CONSTRAINT ck_seckill_result_entry_type CHECK(event_type IN('ALLOCATE','COMPENSATE','ACCEPT','ORDER_CREATED','CREATE_CLOSED','ORDER_PAID','ORDER_CANCELLED'));
SELECT 'SECKILL_ORDER_RESOURCES_READY';
