-- REQ-015 / DEC-046/057/065/066. Product-owned allocation prerequisite only.
-- This installs the evolved TWO product tables from the historical, UNAPPLIED V002 seckill design.
-- Do not execute product/V002 as well. No promotion/trade tables or business inventory writes here.
-- DDL implicitly commits; on error inspect partial state instead of replaying this file.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_allocation_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_allocation_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('product_spu','product_sku') AND ENGINE='InnoDB')=2
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_sku'
  AND COLUMN_NAME IN('id','tenant_id','spu_id','stock','status','auth_version','deleted'))=7
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_seckill_allocation','product_seckill_stock_entry'))
 AND NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME LIKE 'ck_seckill_product_%'),1,0);

CREATE TABLE product_seckill_allocation (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本服务划拨凭证ID',
 tenant_id BIGINT NOT NULL COMMENT '可信租户，不接受浏览器指定',
 activity_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'promotion活动号',
 generation INT UNSIGNED NOT NULL COMMENT '冻结发布代次，重新发布使用新代次',
 spu_id BIGINT NOT NULL COMMENT '本服务原商品SPU',
 sku_id BIGINT NOT NULL COMMENT '本服务原商品SKU',
 spu_version BIGINT NOT NULL COMMENT '冻结的商品资料版本',
 sku_version BIGINT NOT NULL COMMENT '冻结的规格资料版本',
 operation_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本代次本SKU稳定业务键',
 payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范原请求摘要，非认证凭据',
 quantity INT UNSIGNED NOT NULL COMMENT '原计划划拨量，关闭先到时也保留',
 allocated_quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '实际划拨量；关闭先到为0',
 returned_quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '实际累计归还量，不大于实际划拨量',
 status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0事务内待处理10已划拨20已补偿或划拨前关闭30已结清40待核查',
 version BIGINT NOT NULL DEFAULT 0 COMMENT '凭证条件更新版本',
 create_time DATETIME(3) NOT NULL COMMENT '显式UTC写入',
 update_time DATETIME(3) NOT NULL COMMENT '显式UTC写入',
 PRIMARY KEY(id),
 UNIQUE KEY uk_allocation_operation(tenant_id,operation_no),
 UNIQUE KEY uk_allocation_sku(tenant_id,activity_no,generation,sku_id),
 KEY idx_allocation_scan(status,update_time,id),
 CONSTRAINT ck_seckill_product_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND spu_id BETWEEN 1 AND 9007199254740991
  AND sku_id BETWEEN 1 AND 9007199254740991 AND generation BETWEEN 1 AND 2147483647),
 CONSTRAINT ck_seckill_product_versions CHECK(spu_version BETWEEN 0 AND 9007199254740991 AND sku_version BETWEEN 0 AND 9007199254740991
  AND version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_seckill_product_keys CHECK(activity_no REGEXP '^[A-Za-z0-9][A-Za-z0-9:_-]{0,63}$'
  AND operation_no REGEXP '^[A-Za-z0-9][A-Za-z0-9:_-]{0,63}$' AND payload_hash REGEXP '^[0-9a-f]{64}$'),
 CONSTRAINT ck_seckill_product_quantity CHECK(quantity BETWEEN 1 AND 2147483647 AND allocated_quantity IN(0,quantity)
  AND returned_quantity<=allocated_quantity),
 CONSTRAINT ck_seckill_product_state CHECK(status IN(0,10,20,30,40)
  AND (status<>0 OR (allocated_quantity=0 AND returned_quantity=0))
  AND (status<>10 OR allocated_quantity=quantity)
  AND (status NOT IN(20,30) OR returned_quantity=allocated_quantity))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='普通库存到秒杀专属池的持久划拨与关闭凭证';

CREATE TABLE product_seckill_stock_entry (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 allocation_id BIGINT NOT NULL COMMENT '本服务划拨凭证逻辑关联，无物理外键',
 sku_id BIGINT NOT NULL,
 operation_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原划拨键+固定变动类型',
 payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ALLOCATE/COMPENSATE；RETURN保留给后续清算',
 quantity INT UNSIGNED NOT NULL COMMENT '正数量，方向由类型确定',
 before_stock INT NOT NULL COMMENT '本次普通可售变动前',
 after_stock INT NOT NULL COMMENT '本次普通可售变动后',
 reason VARCHAR(500) NOT NULL DEFAULT '',
 create_time DATETIME(3) NOT NULL COMMENT '显式UTC记账，禁止删除',
 PRIMARY KEY(id),
 UNIQUE KEY uk_stock_operation(tenant_id,operation_no),
 KEY idx_stock_allocation(tenant_id,allocation_id,id),
 CONSTRAINT ck_seckill_product_entry_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND allocation_id>0 AND sku_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_product_entry_quantity CHECK(quantity BETWEEN 1 AND 2147483647 AND before_stock>=0 AND after_stock>=0),
 CONSTRAINT ck_seckill_product_entry_key CHECK(CHAR_LENGTH(operation_no) BETWEEN 1 AND 128 AND payload_hash REGEXP '^[0-9a-f]{64}$'),
 CONSTRAINT ck_seckill_product_entry_movement CHECK(event_type IN('ALLOCATE','COMPENSATE','RETURN')
  AND CAST(after_stock AS SIGNED)=CAST(before_stock AS SIGNED)+IF(event_type='ALLOCATE',-CAST(quantity AS SIGNED),CAST(quantity AS SIGNED)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='与SKU变动同事务的秒杀划拨库存流水';
SELECT 'SECKILL_PRODUCT_ALLOCATION_LEDGER_READY' AS result;
