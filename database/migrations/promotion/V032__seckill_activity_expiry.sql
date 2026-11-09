-- DEC-046. No business rows, leases, jobs or fake user identities initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_expiry_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_expiry_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')=14
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_redis_epoch WHERE status=30)
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_cache_cursor')=8
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch' AND COLUMN_NAME='stop_cache_state')
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_activity_audit'
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND ENFORCED='YES'),1,0);
ALTER TABLE promotion_seckill_redis_epoch
 ADD COLUMN stop_cache_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_REQUIRED',
 ADD CONSTRAINT ck_seckill_epoch_stop_cache CHECK((status IN(0,10,15,20) AND stop_cache_state='NOT_REQUIRED')
  OR (status=30 AND stop_cache_state IN('PENDING','SYNCED')));
ALTER TABLE promotion_seckill_cache_cursor
 ADD COLUMN after_expiry_activity BIGINT NULL,
 ADD CONSTRAINT ck_promo_seckill_expiry_position CHECK(after_expiry_activity IS NULL OR after_expiry_activity BETWEEN 1 AND 9007199254740991);
ALTER TABLE promotion_seckill_job_cursor DROP CHECK ck_promo_seckill_job_code,
 ADD CONSTRAINT ck_promo_seckill_job_code CHECK(job_code IN('SECKILL_COMMAND_PUBLISH','CLOSED_CACHE_SYNC','REQUEST_RECONCILE','INTENT_RECOVERY','ORDER_CACHE_SYNC','ORDER_RESOURCE_SYNC','POOL_RETURN_RECOVERY','ACTIVITY_EXPIRE'));
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_identity,
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND ((action='EPOCH_EXPIRE' AND actor_id=0) OR (action<>'EPOCH_EXPIRE' AND actor_id BETWEEN 1 AND 9007199254740991))),
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP','EPOCH_EXPIRE')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP','EPOCH_EXPIRE')
   AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_ACTIVITY_EXPIRY_READY';
