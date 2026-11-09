-- DEC-046/057/065/066 / REQ-015. Product guard + immutable preparation receipt, no business records.
-- Install only the SKU guard subset of historical UNAPPLIED promotion/V002; do not replay that script.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_publish_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_publish_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity')=24
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity_sku')=19
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity_audit')=9
 AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_activity_audit' AND CONSTRAINT_NAME IN('ck_seckill_draft_audit_action','ck_seckill_draft_audit_version') AND ENFORCED='YES')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('promotion_seckill_sku_guard','promotion_seckill_publish_record'))
 AND NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME LIKE 'ck_seckill_publish_%'),1,0);

CREATE TABLE promotion_seckill_sku_guard (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 create_time DATETIME(3) NOT NULL COMMENT '显式UTC；互斥行不能随活动删除',
 PRIMARY KEY(id),
 UNIQUE KEY uk_guard_sku(tenant_id,sku_id),
 CONSTRAINT ck_seckill_publish_guard_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND sku_id BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同租户SKU时间冲突发布互斥行';

CREATE TABLE promotion_seckill_publish_record (
 tenant_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原发布UUID，重复不换号',
 activity_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 from_version BIGINT NOT NULL,
 prepared_version BIGINT NOT NULL,
 generation INT UNSIGNED NOT NULL,
 request_snapshot JSON NOT NULL COMMENT '冻结的每SKU原业务号及商品/版本/数量，不存凭据',
 create_time DATETIME(3) NOT NULL COMMENT '显式UTC；记录不可变',
 PRIMARY KEY(tenant_id,request_id),
 UNIQUE KEY uk_seckill_publish_generation(tenant_id,activity_id,generation),
 CONSTRAINT ck_seckill_publish_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND actor_id BETWEEN 1 AND 9007199254740991 AND generation BETWEEN 1 AND 2147483647),
 CONSTRAINT ck_seckill_publish_versions CHECK(from_version BETWEEN 0 AND 9007199254740990 AND prepared_version=from_version+1),
 CONSTRAINT ck_seckill_publish_request CHECK(request_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
 CONSTRAINT ck_seckill_publish_snapshot CHECK(JSON_TYPE(request_snapshot)='ARRAY' AND JSON_LENGTH(request_snapshot) BETWEEN 1 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀发布代次冻结请求与原号恢复凭证';

ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_PUBLISH_PREPARATION_SCHEMA_READY' AS result;
