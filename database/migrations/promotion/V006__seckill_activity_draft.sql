-- REQ-015 / DEC-046/057/065/066. Evolved activity/SKU subset of UNAPPLIED historical promotion/V002.
-- Do not execute promotion/V002 after this migration; its remaining six tables are not installed here.
-- Draft configuration and audit only. No activity, inventory, grant or role seed data.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_activity_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_activity_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('promotion_coupon_template','promotion_coupon') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('promotion_seckill_activity','promotion_seckill_activity_sku','promotion_seckill_activity_audit'))
 AND NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME LIKE 'ck_seckill_draft_%'),1,0);

CREATE TABLE promotion_seckill_activity (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 activity_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务器生成的活动UUID，不充当授权',
 name VARCHAR(60) NOT NULL,
 product_spu_id BIGINT NOT NULL,
 product_spu_version BIGINT NOT NULL COMMENT '保存时的真实商品资料版本，发布需重新核对',
 product_name VARCHAR(128) NOT NULL COMMENT '商品服务返回的名称快照',
 pic_url VARCHAR(1024) NOT NULL COMMENT '商品服务返回的封面快照',
 start_time DATETIME(3) NOT NULL COMMENT 'UTC，包含',
 end_time DATETIME(3) NOT NULL COMMENT 'UTC，不包含',
 single_limit INT UNSIGNED NOT NULL,
 member_limit INT UNSIGNED NOT NULL,
 status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0草稿10准备20发布30停止40清算50结束60发布失败',
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 generation INT UNSIGNED NOT NULL DEFAULT 0,
 redis_epoch BIGINT UNSIGNED NOT NULL DEFAULT 0,
 publish_operation_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
 stop_reason VARCHAR(500) NOT NULL DEFAULT '',
 stopped_by VARCHAR(64) NOT NULL DEFAULT '',
 creator VARCHAR(64) NOT NULL,
 create_time DATETIME(3) NOT NULL COMMENT '显式UTC写入',
 updater VARCHAR(64) NOT NULL,
 update_time DATETIME(3) NOT NULL COMMENT '显式UTC写入',
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_activity_no(tenant_id,activity_no),
 UNIQUE KEY uk_activity_publish(tenant_id,publish_operation_no),
 KEY idx_activity_tenant_state(tenant_id,deleted,status,start_time,id),
 KEY idx_activity_reconcile(status,end_time,id),
 CONSTRAINT ck_seckill_draft_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND product_spu_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_draft_version CHECK(version<=9007199254740991 AND redis_epoch<=9007199254740991
  AND product_spu_version BETWEEN 0 AND 9007199254740991 AND generation<=2147483647),
 CONSTRAINT ck_seckill_draft_time CHECK(start_time>=TIMESTAMP'2000-01-01 00:00:00' AND start_time<end_time AND end_time<TIMESTAMP'2100-01-01 00:00:00'),
 CONSTRAINT ck_seckill_draft_limits CHECK(single_limit BETWEEN 1 AND 9999 AND member_limit BETWEEN single_limit AND 2147483647),
 CONSTRAINT ck_seckill_draft_status CHECK(status IN(0,10,20,30,40,50,60)),
 CONSTRAINT ck_seckill_draft_name CHECK(CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 60 AND CHAR_LENGTH(product_name)>0 AND CHAR_LENGTH(pic_url)>0),
 CONSTRAINT ck_seckill_draft_key CHECK(activity_no REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动配置与发布状态';

CREATE TABLE promotion_seckill_activity_sku (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 sku_version BIGINT NOT NULL COMMENT '保存时真实规格资料版本，非库存数量版本',
 spec_snapshot JSON NOT NULL COMMENT '商品服务返回的销售规格快照',
 price_cent BIGINT UNSIGNED NOT NULL COMMENT '活动单价，整数分',
 planned_quantity INT UNSIGNED NOT NULL,
 allocated_quantity INT UNSIGNED NOT NULL DEFAULT 0,
 available_quantity INT UNSIGNED NOT NULL DEFAULT 0,
 held_quantity INT UNSIGNED NOT NULL DEFAULT 0,
 sold_quantity INT UNSIGNED NOT NULL DEFAULT 0,
 returned_quantity INT UNSIGNED NOT NULL DEFAULT 0,
 return_pending_quantity INT UNSIGNED NOT NULL DEFAULT 0,
 allocation_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未划拨10处理中20成功30归还中40已清算50待核查',
 allocation_operation_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 create_time DATETIME(3) NOT NULL,
 update_time DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_activity_sku(tenant_id,activity_id,sku_id),
 KEY idx_sku_conflict(tenant_id,sku_id,activity_id),
 CONSTRAINT ck_seckill_draft_sku_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND sku_id BETWEEN 1 AND 9007199254740991 AND sku_version BETWEEN 0 AND 9007199254740991 AND version<=9007199254740991),
 CONSTRAINT ck_seckill_draft_sku_price CHECK(price_cent BETWEEN 1 AND 2000000000 AND planned_quantity BETWEEN 1 AND 2147483647),
 CONSTRAINT ck_seckill_draft_sku_balance CHECK(allocated_quantity<=2147483647
  AND allocated_quantity=available_quantity+held_quantity+sold_quantity+returned_quantity AND return_pending_quantity<=available_quantity),
 CONSTRAINT ck_seckill_draft_sku_state CHECK(allocation_status IN(0,10,20,30,40,50)),
 CONSTRAINT ck_seckill_draft_sku_snapshot CHECK(JSON_TYPE(spec_snapshot)='ARRAY' AND JSON_LENGTH(spec_snapshot) BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动规格计划及权威额度池';

CREATE TABLE promotion_seckill_activity_audit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 details JSON NOT NULL,
 create_time DATETIME(3) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_seckill_activity_audit_version(tenant_id,activity_id,to_version),
 KEY idx_seckill_activity_audit_actor(tenant_id,actor_id,id),
 CONSTRAINT ck_seckill_draft_audit_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND actor_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE')),
 CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action='UPDATE' AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1)),
 CONSTRAINT ck_seckill_draft_audit_details CHECK(JSON_TYPE(details)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='与活动草稿同事务的配置审计';
SELECT 'SECKILL_ACTIVITY_DRAFT_SCHEMA_READY' AS result;
