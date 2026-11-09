-- DEC-046/065/066. Compatible expansion of existing fixed recovery/replay types.
-- Does not register/start XXL, seed work, receive orders or alter existing rows.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE auto_receive_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO auto_receive_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME IN('ck_trade_recovery_kind','ck_trade_replay_kind') AND CHECK_CLAUSE LIKE '%CREATE_COMPENSATE%'
  AND CHECK_CLAUSE NOT LIKE '%AUTO_RECEIVE%')=2
 AND EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='trade_order' AND INDEX_NAME='idx_trade_order_auto_receive')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery')=16
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery_replay')=20,1,0);
ALTER TABLE trade_order_recovery DROP CHECK ck_trade_recovery_kind,
 ADD CONSTRAINT ck_trade_recovery_kind CHECK(kind IN('PAY_SYNC','CANCEL_EXPIRED','CANCEL_RELEASE','CREATE_COMPENSATE','AUTO_RECEIVE'));
ALTER TABLE trade_order_recovery_replay DROP CHECK ck_trade_replay_kind,
 ADD CONSTRAINT ck_trade_replay_kind CHECK(kind IN('PAY_SYNC','CANCEL_EXPIRED','CANCEL_RELEASE','CREATE_COMPENSATE','AUTO_RECEIVE'));
SELECT 'AUTO_RECEIVE_RECOVERY_KIND_ADDED' AS result;
