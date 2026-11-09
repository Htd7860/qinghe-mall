-- DEC-046. Cold-stop is not a runtime permit. Preserve every existing runtime token and balance.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_cold_stop_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_cold_stop_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')=15
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_epoch_initial' AND CHECK_CLAUSE NOT LIKE '%40%')
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='promotion_seckill_redis_epoch' AND CONSTRAINT_NAME='ck_seckill_epoch_stop_cache' AND ENFORCED='YES'),1,0);
ALTER TABLE promotion_seckill_redis_epoch
 MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0准备10暂停seed15运行初始化许可20就绪30运行后停止40从未发运行许可已关闭',
 DROP CHECK ck_seckill_epoch_initial,
 DROP CHECK ck_seckill_epoch_init_window,
 DROP CHECK ck_seckill_epoch_stop_cache,
 ADD CONSTRAINT ck_seckill_epoch_initial CHECK(epoch=1 AND status IN(0,10,15,20,30,40)),
 ADD CONSTRAINT ck_seckill_epoch_init_window CHECK((status IN(0,10,40) AND init_token IS NULL AND init_deadline IS NULL)
  OR (status IN(15,20,30) AND init_token IS NOT NULL AND init_deadline IS NOT NULL AND init_deadline>create_time
   AND REGEXP_LIKE(init_token,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c'))),
 ADD CONSTRAINT ck_seckill_epoch_stop_cache CHECK((status IN(0,10,15,20,40) AND stop_cache_state='NOT_REQUIRED')
  OR (status=30 AND stop_cache_state IN('PENDING','SYNCED')));
SELECT 'SECKILL_PRE_RUNTIME_EXPIRY_READY';
