-- DEC-046/057/065/066 / REQ-015. Extend existing activity audit for final original-generation decisions.
-- No business status update, inventory/role/package seed or new table.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_decision_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_decision_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_stock_receipt')=9
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_quota_entry')=17
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity_audit')=9
 AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_activity_audit' AND CONSTRAINT_NAME IN('ck_seckill_draft_audit_action','ck_seckill_draft_audit_version') AND ENFORCED='YES')=2
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%FAIL%' AND CHECK_CLAUSE NOT LIKE '%PUBLISH%'),1,0);
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_PUBLISH_DECISION_AUDIT_READY' AS result;
