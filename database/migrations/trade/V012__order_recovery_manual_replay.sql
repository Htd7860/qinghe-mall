-- DEC-046/065/066. One typed order-replay audit, not a general audit/retry framework.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE order_replay_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO order_replay_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery' AND ENGINE='InnoDB')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery_replay')
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery' AND COLUMN_NAME='active_replay_id'),1,0);
ALTER TABLE trade_order_recovery ADD COLUMN active_replay_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER trace_id;
CREATE TABLE trade_order_recovery_replay (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 recovery_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id BIGINT NOT NULL,
 reason VARCHAR(200) NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 previous_attempts INT NOT NULL,
 previous_error_code INT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 last_error_code INT NULL,
 last_job_id BIGINT NULL,
 trace_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 last_version BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_replay_request(tenant_id,request_id),
 UNIQUE KEY uk_trade_replay_version(tenant_id,recovery_id,from_version),
 CONSTRAINT ck_trade_replay_identity CHECK(tenant_id>0 AND recovery_id>0 AND order_id>0 AND actor_id>0),
 CONSTRAINT ck_trade_replay_kind CHECK(kind IN('PAY_SYNC','CANCEL_EXPIRED','CANCEL_RELEASE','CREATE_COMPENSATE')),
 CONSTRAINT ck_trade_replay_request CHECK(REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
 CONSTRAINT ck_trade_replay_reason CHECK(CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 200),
 CONSTRAINT ck_trade_replay_version CHECK(from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1 AND last_version BETWEEN to_version AND 9007199254740991),
 CONSTRAINT ck_trade_replay_state CHECK(previous_attempts BETWEEN 1 AND 10 AND
  ((status='READY' AND attempts BETWEEN 0 AND 9) OR (status IN('RUNNING','DONE','MANUAL') AND attempts BETWEEN 1 AND 10))),
 CONSTRAINT ck_trade_replay_error CHECK((last_error_code IS NULL OR last_error_code IN(400,401,403,404,409,429,500,503))
  AND (previous_error_code IS NULL OR previous_error_code IN(400,401,403,404,409,429,500,503)) AND (last_job_id IS NULL OR last_job_id>0)),
 CONSTRAINT ck_trade_replay_time CHECK(update_time>=create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'ORDER_RECOVERY_MANUAL_REPLAY_CREATED' AS result;
