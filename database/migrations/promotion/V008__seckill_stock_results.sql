-- DEC-046/057/065/066 / REQ-015. Add durable product receipts and immutable pool movements.
-- Quota entry evolves the UNAPPLIED historical promotion/V002 subset; do not replay V002.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_result_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_result_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity')=24
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity_sku')=19
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_publish_record')=9
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('promotion_seckill_stock_receipt','promotion_seckill_quota_entry'))
 AND NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME LIKE 'ck_seckill_result_%'),1,0);

CREATE TABLE promotion_seckill_stock_receipt (
 tenant_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT UNSIGNED NOT NULL,
 sku_id BIGINT NOT NULL,
 product_version BIGINT NOT NULL,
 product_status TINYINT NOT NULL COMMENT '10实际划拨20已补偿/关闭，不存未知推断',
 allocated_quantity INT NOT NULL,
 returned_quantity INT NOT NULL,
 create_time DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,activity_id,generation,sku_id,product_version),
 CONSTRAINT ck_seckill_result_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND generation BETWEEN 1 AND 2147483647 AND sku_id BETWEEN 1 AND 9007199254740991 AND product_version BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_result_state CHECK(product_status IN(10,20) AND allocated_quantity>=0 AND returned_quantity BETWEEN 0 AND allocated_quantity
  AND (product_status<>10 OR (allocated_quantity>0 AND returned_quantity=0)) AND (product_status<>20 OR returned_quantity=allocated_quantity))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='product原划拨版本的不可变已确认回执';

CREATE TABLE promotion_seckill_quota_entry (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 operation_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 activity_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 member_id BIGINT DEFAULT NULL,
 request_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
 event_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 available_delta INT NOT NULL DEFAULT 0,
 held_delta INT NOT NULL DEFAULT 0,
 sold_delta INT NOT NULL DEFAULT 0,
 returned_delta INT NOT NULL DEFAULT 0,
 member_held_delta INT NOT NULL DEFAULT 0,
 member_paid_delta INT NOT NULL DEFAULT 0,
 allocated_delta INT NOT NULL DEFAULT 0,
 source_event_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 create_time DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_quota_operation(tenant_id,operation_no),
 KEY idx_quota_request(tenant_id,request_no,id),
 KEY idx_quota_activity(tenant_id,activity_id,sku_id,id),
 CONSTRAINT ck_seckill_result_entry_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND sku_id BETWEEN 1 AND 9007199254740991 AND (member_id IS NULL OR member_id BETWEEN 1 AND 9007199254740991)),
 CONSTRAINT ck_seckill_result_entry_balance CHECK(CAST(allocated_delta AS SIGNED)=CAST(available_delta AS SIGNED)+CAST(held_delta AS SIGNED)
  +CAST(sold_delta AS SIGNED)+CAST(returned_delta AS SIGNED)),
 CONSTRAINT ck_seckill_result_entry_type CHECK(event_type IN('ALLOCATE','COMPENSATE')),
 CONSTRAINT ck_seckill_result_entry_keys CHECK(CHAR_LENGTH(operation_no) BETWEEN 1 AND 128 AND CHAR_LENGTH(source_event_id) BETWEEN 1 AND 64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='与活动池更新同事务的不可变额度变化';

ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_STOCK_RESULT_SCHEMA_READY' AS result;
