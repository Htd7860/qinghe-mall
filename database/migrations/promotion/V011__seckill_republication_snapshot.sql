-- DEC-046/057/065/066 / REQ-015. Preserve full configuration before enabling new publishing generations.
-- Current local receipt table must be empty: do not invent snapshots for existing business records.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_republish_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_republish_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_publish_record')=9
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_publish_record)
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%WITHDRAW%' AND CHECK_CLAUSE NOT LIKE '%REOPEN%'),1,0);
ALTER TABLE promotion_seckill_publish_record
 ADD COLUMN configuration_snapshot JSON NOT NULL COMMENT '不可变的当代完整活动/规格配置，与库存请求快照同事务',
 ADD CONSTRAINT ck_seckill_republish_configuration CHECK(JSON_TYPE(configuration_snapshot)='OBJECT'
  AND JSON_CONTAINS_PATH(configuration_snapshot,'all','$.activity','$.skus')
  AND JSON_TYPE(JSON_EXTRACT(configuration_snapshot,'$.activity'))='OBJECT'
  AND JSON_TYPE(JSON_EXTRACT(configuration_snapshot,'$.skus'))='ARRAY'
  AND JSON_LENGTH(JSON_EXTRACT(configuration_snapshot,'$.skus')) BETWEEN 1 AND 200);
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_REPUBLICATION_SNAPSHOT_READY' AS result;
