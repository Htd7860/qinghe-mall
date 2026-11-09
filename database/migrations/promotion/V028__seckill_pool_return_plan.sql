-- DEC-046. Frozen original closed-pool return target; no product RPC or business stock initialization.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_pool_return_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_pool_return_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity_sku')=19
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_pool_return'),1,0);
CREATE TABLE promotion_seckill_pool_return (
 tenant_id BIGINT NOT NULL,
 return_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 redis_epoch BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 allocation_operation_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 allocation_snapshot JSON NOT NULL,
 quantity INT NOT NULL,
 base_returned INT NOT NULL,
 target_returned INT NOT NULL,
 base_product_version BIGINT NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
 product_version BIGINT NULL,
 product_status INT NULL,
 cache_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_REQUIRED',
 version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(3) NOT NULL,
 update_time DATETIME(3) NOT NULL,
 confirmed_time DATETIME(3) NULL,
 active_sku BIGINT GENERATED ALWAYS AS (CASE WHEN state='PENDING' THEN sku_id ELSE NULL END) STORED,
 PRIMARY KEY(tenant_id,return_no),
 UNIQUE KEY uk_seckill_pool_return_active(tenant_id,activity_id,generation,active_sku),
 UNIQUE KEY uk_seckill_pool_return_target(tenant_id,activity_id,generation,sku_id,target_returned),
 KEY idx_seckill_pool_return_state(tenant_id,state,return_no),
 KEY idx_seckill_pool_return_cache(tenant_id,cache_state,return_no),
 CONSTRAINT ck_seckill_pool_return_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND generation>0 AND redis_epoch=1 AND sku_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_pool_return_keys CHECK(REGEXP_LIKE(return_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(allocation_operation_no,'^[A-Za-z0-9][A-Za-z0-9:_-]{0,63}$','c')),
 CONSTRAINT ck_seckill_pool_return_amount CHECK(quantity BETWEEN 1 AND 2147483647 AND base_returned>=0
  AND CAST(target_returned AS SIGNED)=CAST(base_returned AS SIGNED)+CAST(quantity AS SIGNED) AND target_returned BETWEEN 1 AND 2147483647),
 CONSTRAINT ck_seckill_pool_return_snapshot CHECK(JSON_TYPE(allocation_snapshot)='OBJECT' AND JSON_LENGTH(allocation_snapshot)=8
  AND JSON_CONTAINS_PATH(allocation_snapshot,'all','$.activityNo','$.generation','$.operationNo','$.spuId','$.skuId','$.spuVersion','$.skuVersion','$.quantity')
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.activityNo'))='STRING'
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.operationNo'))='STRING'
  AND JSON_UNQUOTE(JSON_EXTRACT(allocation_snapshot,'$.operationNo'))=allocation_operation_no
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.generation'))='INTEGER' AND JSON_EXTRACT(allocation_snapshot,'$.generation')=generation
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.skuId'))='INTEGER' AND JSON_EXTRACT(allocation_snapshot,'$.skuId')=sku_id
  AND JSON_TYPE(JSON_EXTRACT(allocation_snapshot,'$.quantity'))='INTEGER' AND JSON_EXTRACT(allocation_snapshot,'$.quantity')>=target_returned),
 CONSTRAINT ck_seckill_pool_return_versions CHECK(base_product_version BETWEEN 1 AND 9007199254740990 AND version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_seckill_pool_return_state CHECK(
  (state='PENDING' AND product_version IS NULL AND product_status IS NULL AND confirmed_time IS NULL AND cache_state='NOT_REQUIRED')
  OR (state='CONFIRMED' AND product_version IS NOT NULL AND product_version=base_product_version+1 AND product_status IS NOT NULL
   AND product_status IN(30,50) AND confirmed_time IS NOT NULL AND confirmed_time>=create_time AND cache_state IN('PENDING','SYNCED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原代次空闲库存归还预留及不可变累计目标';
SELECT 'SECKILL_POOL_RETURN_PLAN_READY';
