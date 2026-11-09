-- DEC-046/057/065/066 / REQ-015. Audit enum extension only; no business row writes.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_withdraw_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_withdraw_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity_audit')=9
 AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_activity_audit' AND CONSTRAINT_NAME IN('ck_seckill_draft_audit_action','ck_seckill_draft_audit_version') AND ENFORCED='YES')=2
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%SETTLE%' AND CHECK_CLAUSE NOT LIKE '%WITHDRAW%'),1,0);
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_BEFORE_START_WITHDRAW_AUDIT_READY' AS result;
