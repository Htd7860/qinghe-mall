-- DEC-046. Activity closure audit only; retain sold obligations and historical refund ledgers.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_clearing_done_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_clearing_done_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%CLEARING%' AND CHECK_CLAUSE NOT LIKE '%CLEARING_DONE%')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_clearing_pool')=16,1,0);
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_identity,
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND ((action IN('EPOCH_EXPIRE','CLEARING','CLEARING_DONE') AND actor_id=0)
   OR (action NOT IN('EPOCH_EXPIRE','CLEARING','CLEARING_DONE') AND actor_id BETWEEN 1 AND 9007199254740991))),
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP','EPOCH_EXPIRE','CLEARING','CLEARING_DONE')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP','EPOCH_EXPIRE','CLEARING','CLEARING_DONE')
   AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_CLEARING_COMPLETION_READY';
