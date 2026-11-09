-- DEC-046. Replace only the unapplied historical V002 inbox/outbox design. Do not replay V002.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_order_receipt_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_order_receipt_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('trade_seckill_inbox','trade_seckill_outbox'))
 AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_prepare' AND COLUMN_NAME='source_type'),1,0);
CREATE TABLE trade_seckill_outbox (
 tenant_id BIGINT NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 aggregate_version BIGINT NOT NULL,
 schema_version INT NOT NULL DEFAULT 1,
 payload JSON NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 available_at DATETIME(3) NOT NULL,
 published_at DATETIME(3) NULL,
 lease_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(3) NULL,
 last_error_code INT NULL,
 replay_version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,event_id),
 UNIQUE KEY uk_trade_sc_outbox_aggregate(tenant_id,request_no,aggregate_version,event_type),
 KEY idx_trade_sc_outbox_delivery(tenant_id,status,available_at,lease_until,event_id),
 CONSTRAINT ck_trade_sc_outbox_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND aggregate_version BETWEEN 0 AND 9007199254740991
  AND replay_version BETWEEN 0 AND 9007199254740991 AND schema_version=1),
 CONSTRAINT ck_trade_sc_outbox_keys CHECK(REGEXP_LIKE(event_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
 CONSTRAINT ck_trade_sc_outbox_state CHECK(event_type IN('SECKILL_ORDER_CREATED','SECKILL_REQUEST_CLOSED') AND status IN('PENDING','SENDING','PUBLISHED','MANUAL') AND attempts BETWEEN 0 AND 10),
 CONSTRAINT ck_trade_sc_outbox_lease CHECK((status='SENDING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
  OR (status<>'SENDING' AND lease_owner IS NULL AND lease_until IS NULL)),
 CONSTRAINT ck_trade_sc_outbox_payload CHECK(JSON_TYPE(payload)='OBJECT' AND JSON_LENGTH(payload)=4
  AND JSON_CONTAINS_PATH(payload,'all','$.schemaVersion','$.tenantId','$.requestNo','$.aggregateVersion')
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.schemaVersion'))='INTEGER' AND JSON_EXTRACT(payload,'$.schemaVersion')=1
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.tenantId'))='INTEGER' AND JSON_EXTRACT(payload,'$.tenantId')=tenant_id
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.requestNo'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.requestNo'))=request_no
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.aggregateVersion'))='INTEGER' AND JSON_EXTRACT(payload,'$.aggregateVersion')=aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='trade建单裁决结果引用；未接发送任务';

CREATE TABLE trade_seckill_inbox (
 tenant_id BIGINT NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 aggregate_version BIGINT NOT NULL,
 event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result_state TINYINT NOT NULL,
 order_id BIGINT NULL,
 result_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 processed_at DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,event_id),
 KEY idx_trade_sc_inbox_request(tenant_id,request_no,event_id),
 CONSTRAINT ck_trade_sc_inbox_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND aggregate_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_trade_sc_inbox_keys CHECK(
  REGEXP_LIKE(event_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(result_event_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(payload_hash,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_trade_sc_inbox_result CHECK(event_type IN('SECKILL_CREATE','SECKILL_CLOSE')
  AND ((result_state=10 AND order_id IS NOT NULL AND order_id BETWEEN 1 AND 9007199254740991)
    OR (result_state=20 AND order_id IS NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='only committed final consumption receipts, same transaction as order/fence/outbox';
SELECT 'SECKILL_ORDER_RECEIPTS_READY';
