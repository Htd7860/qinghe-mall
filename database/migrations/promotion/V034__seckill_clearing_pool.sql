-- DEC-046. Immutable per-generation baseline only; does not declare orders/funds settled or reset pools.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_clearing_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_clearing_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_clearing_pool')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')=15
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%EPOCH_EXPIRE%' AND CHECK_CLAUSE NOT LIKE '%CLEARING%'),1,0);
CREATE TABLE promotion_seckill_clearing_pool (
 tenant_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 redis_epoch BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 allocation_operation_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allocation_snapshot JSON NOT NULL,
 allocated_quantity INT NOT NULL,
 sold_quantity INT NOT NULL,
 returned_quantity INT NOT NULL,
 product_version BIGINT NOT NULL,
 product_status TINYINT NOT NULL,
 definition_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 pool_version BIGINT NOT NULL,
 activity_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,activity_id,generation,sku_id),
 UNIQUE KEY uk_seckill_clearing_operation(tenant_id,activity_id,generation,allocation_operation_no),
 CONSTRAINT ck_seckill_clearing_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND sku_id BETWEEN 1 AND 9007199254740991 AND generation BETWEEN 1 AND 2147483647 AND redis_epoch=1),
 CONSTRAINT ck_seckill_clearing_balance CHECK(allocated_quantity BETWEEN 1 AND 2147483647 AND sold_quantity>=0 AND returned_quantity>=0
  AND CAST(sold_quantity AS SIGNED)+CAST(returned_quantity AS SIGNED)=allocated_quantity),
 CONSTRAINT ck_seckill_clearing_receipt CHECK((product_status=10 AND returned_quantity=0)
  OR (product_status=30 AND returned_quantity=allocated_quantity) OR (product_status=50 AND returned_quantity>0 AND sold_quantity>0)),
 CONSTRAINT ck_seckill_clearing_versions CHECK(product_version BETWEEN 1 AND 9007199254740991 AND pool_version BETWEEN 0 AND 9007199254740991
  AND activity_version BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_clearing_keys CHECK(REGEXP_LIKE(allocation_operation_no,'^[A-Za-z0-9][A-Za-z0-9:_-]{0,63}$','c')
  AND REGEXP_LIKE(definition_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_seckill_clearing_snapshot CHECK(JSON_TYPE(allocation_snapshot)='OBJECT' AND JSON_LENGTH(allocation_snapshot)=8
  AND JSON_CONTAINS_PATH(allocation_snapshot,'all','$.activityNo','$.generation','$.operationNo','$.spuId','$.skuId','$.spuVersion','$.skuVersion','$.quantity')
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.generation'))='INTEGER' AND JSON_EXTRACT(allocation_snapshot,'$.generation')=generation
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.skuId'))='INTEGER' AND JSON_EXTRACT(allocation_snapshot,'$.skuId')=sku_id
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.quantity'))='INTEGER' AND JSON_EXTRACT(allocation_snapshot,'$.quantity')=allocated_quantity
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.operationNo'))='STRING'
  AND JSON_UNQUOTE(JSON_EXTRACT(allocation_snapshot,'$.operationNo'))=allocation_operation_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='immutable original generation clearing baseline, sold obligations retained';
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_identity,
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND ((action IN('EPOCH_EXPIRE','CLEARING') AND actor_id=0) OR (action NOT IN('EPOCH_EXPIRE','CLEARING') AND actor_id BETWEEN 1 AND 9007199254740991))),
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP','EPOCH_EXPIRE','CLEARING')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP','EPOCH_EXPIRE','CLEARING')
   AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_CLEARING_POOL_READY';
