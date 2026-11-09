-- DEC-046/057/065/066. Expand paused epoch receipt only, no READY or business updates.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_seed_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_seed_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')=12
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_epoch_initial' AND CHECK_CLAUSE NOT LIKE '%10%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%EPOCH_INIT%' AND CHECK_CLAUSE NOT LIKE '%EPOCH_SEED%'),1,0);
ALTER TABLE promotion_seckill_redis_epoch
 MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0准备中，10曾确认暂停seed；均不接单，尚无READY',
 DROP CHECK ck_seckill_epoch_initial,
 ADD CONSTRAINT ck_seckill_epoch_initial CHECK(epoch=1 AND status IN(0,10));
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_PAUSED_SEED_SCHEMA_READY';
