-- DEC-046/057/065/066. Only original-event publication replay, separate from business recovery.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE trade_event_replay_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO trade_event_replay_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_outbox')=15
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_event_receipt')=6
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_outbox' AND COLUMN_NAME='replay_version')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_event_replay'),1,0);
ALTER TABLE trade_order_outbox
 ADD COLUMN replay_version BIGINT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_trade_outbox_replay_version CHECK(replay_version BETWEEN 0 AND 9007199254740991);
CREATE TABLE trade_order_event_replay (
 tenant_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id BIGINT NOT NULL,
 reason VARCHAR(200) NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 previous_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 previous_attempts INT NOT NULL,
 previous_error_code INT NULL,
 previous_published_at DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,request_id),
 UNIQUE KEY uk_trade_event_replay_generation(tenant_id,event_id,from_version),
 CONSTRAINT ck_event_replay_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND actor_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_event_replay_uuid CHECK(
  REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(event_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
 CONSTRAINT ck_event_replay_reason CHECK(CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 200),
 CONSTRAINT ck_event_replay_version CHECK(from_version BETWEEN 0 AND 9007199254740990 AND to_version=from_version+1),
 CONSTRAINT ck_event_replay_previous CHECK(
  (previous_status='MANUAL' AND previous_attempts BETWEEN 1 AND 10 AND previous_published_at IS NULL)
  OR (previous_status='PUBLISHED' AND previous_attempts BETWEEN 0 AND 10 AND previous_published_at IS NOT NULL)),
 CONSTRAINT ck_event_replay_error CHECK(previous_error_code IS NULL OR previous_error_code=503),
 CONSTRAINT ck_event_replay_time CHECK(previous_published_at IS NULL OR create_time>=previous_published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'TRADE_EVENT_MANUAL_REPLAY_READY' AS result;
