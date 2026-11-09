-- DEC-046/057/065/066. Fixed first-runtime permit; no READY or business initialization.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_runtime_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_runtime_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')=12
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_epoch_initial' AND CHECK_CLAUSE LIKE '%10%' AND CHECK_CLAUSE NOT LIKE '%15%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%EPOCH_SEED%' AND CHECK_CLAUSE NOT LIKE '%EPOCH_RUNTIME%'),1,0);
ALTER TABLE promotion_seckill_redis_epoch
 ADD COLUMN init_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '固定首次运行初始化许可，重复不换号',
 ADD COLUMN init_deadline DATETIME(3) NULL COMMENT 'DB UTC时间+30秒与活动结束的较早值；重复不续期',
 MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0准备，10曾确认seed，15固定运行初始化窗口；均不接单',
 DROP CHECK ck_seckill_epoch_initial,
 ADD CONSTRAINT ck_seckill_epoch_initial CHECK(epoch=1 AND status IN(0,10,15)),
 ADD CONSTRAINT ck_seckill_epoch_init_window CHECK((status IN(0,10) AND init_token IS NULL AND init_deadline IS NULL)
  OR (status=15 AND init_token IS NOT NULL AND init_deadline IS NOT NULL AND init_deadline>create_time
   AND REGEXP_LIKE(init_token,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')));
ALTER TABLE promotion_seckill_activity_audit
 DROP CHECK ck_seckill_draft_audit_action,
 DROP CHECK ck_seckill_draft_audit_version,
 ADD CONSTRAINT ck_seckill_draft_audit_action CHECK(action IN('CREATE','UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME')),
 ADD CONSTRAINT ck_seckill_draft_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0)
  OR (action IN('UPDATE','PREPARE','FAIL','PUBLISH','SETTLE','WITHDRAW','REOPEN','EPOCH_INIT','EPOCH_SEED','EPOCH_RUNTIME') AND from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1));
SELECT 'SECKILL_RUNTIME_WINDOW_SCHEMA_READY';
