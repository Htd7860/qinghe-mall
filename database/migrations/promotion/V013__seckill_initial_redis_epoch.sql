-- DEC-046/057/065/066. Initial paused epoch receipt, never an admission-ready marker.
-- Only this known local database; no business rows are inserted or modified by this migration.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_epoch_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_epoch_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity')=24
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_publish_record')=10
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%REOPEN%' AND CHECK_CLAUSE NOT LIKE '%EPOCH_INIT%'),1,0);

CREATE TABLE promotion_seckill_redis_epoch (
 tenant_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 epoch BIGINT NOT NULL COMMENT '本批只允许首次epoch1；后续重建必须独立暂停/核账协议',
 publish_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id BIGINT NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 definition_snapshot JSON NOT NULL COMMENT '不可变定义JSON；无运行可用库存或个人余额',
 definition_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化定义JSON的SHA256',
 status TINYINT NOT NULL DEFAULT 0 COMMENT '0准备中，不接单；尚无READY/重建转换',
 create_time DATETIME(3) NOT NULL COMMENT '显式UTC，原凭证创建时间不续期',
 PRIMARY KEY(tenant_id,activity_id,generation,epoch),
 UNIQUE KEY uk_seckill_epoch_publish(tenant_id,publish_request_id,epoch),
 CONSTRAINT ck_seckill_epoch_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991
  AND activity_id BETWEEN 1 AND 9007199254740991 AND actor_id BETWEEN 1 AND 9007199254740991 AND generation BETWEEN 1 AND 2147483647),
 CONSTRAINT ck_seckill_epoch_initial CHECK(epoch=1 AND status=0),
 CONSTRAINT ck_seckill_epoch_version CHECK(from_version BETWEEN 1 AND 9007199254740990 AND to_version=from_version+1),
 CONSTRAINT ck_seckill_epoch_request CHECK(REGEXP_LIKE(publish_request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
 CONSTRAINT ck_seckill_epoch_digest CHECK(REGEXP_LIKE(definition_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_seckill_epoch_definition CHECK(JSON_TYPE(definition_snapshot)='OBJECT'
  AND JSON_CONTAINS_PATH(definition_snapshot,'all','$.schema','$.tenantId','$.activityId','$.generation','$.publishRequestId','$.skus')
  AND JSON_TYPE(JSON_EXTRACT(definition_snapshot,'$.skus'))='ARRAY'
  AND JSON_LENGTH(JSON_EXTRACT(definition_snapshot,'$.skus')) BETWEEN 1 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀首次Redis初始化原凭证；不可作为准入授权';

ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_INITIAL_EPOCH_SCHEMA_READY';
