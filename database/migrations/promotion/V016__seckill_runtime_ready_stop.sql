-- DEC-046/057/065/066. Epoch readiness/stopping constraints only, not public admission or business initialization.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_ready_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_ready_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')=14
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_epoch_initial' AND CHECK_CLAUSE LIKE '%15%' AND CHECK_CLAUSE NOT LIKE '%20%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%EPOCH_RUNTIME%' AND CHECK_CLAUSE NOT LIKE '%EPOCH_READY%'),1,0);
ALTER TABLE promotion_seckill_redis_epoch
 MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0准备，10确认seed，15初始化窗口，20就绪，30停止新申请；原库存/请求义务保留',
 DROP CHECK ck_seckill_epoch_initial,
 DROP CHECK ck_seckill_epoch_init_window,
 ADD CONSTRAINT ck_seckill_epoch_initial CHECK(epoch=1 AND status IN(0,10,15,20,30)),
 ADD CONSTRAINT ck_seckill_epoch_init_window CHECK((status IN(0,10) AND init_token IS NULL AND init_deadline IS NULL)
  OR (status IN(15,20,30) AND init_token IS NOT NULL AND init_deadline IS NOT NULL AND init_deadline>create_time
   AND REGEXP_LIKE(init_token,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')));
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME','EPOCH_READY','EPOCH_STOP') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_RUNTIME_READY_STOP_SCHEMA_READY';
