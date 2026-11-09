-- DEC-046/057/065/066. Evolves the unapplied V002 request/quota/outbox subset; never replay V002.
-- Creates empty authoritative ledgers. No acceptance, member quota or message is initialized here.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_admission_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_admission_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('promotion_seckill_member_quota','promotion_seckill_request','promotion_seckill_outbox'))
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_quota_entry')=18
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_draft_audit_action' AND CHECK_CLAUSE LIKE '%EPOCH_READY%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_result_entry_type' AND CHECK_CLAUSE NOT LIKE '%ACCEPT%'),1,0);

CREATE TABLE promotion_seckill_member_quota (
 tenant_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 member_id BIGINT NOT NULL,
 held_quantity INT NOT NULL DEFAULT 0,
 paid_quantity INT NOT NULL DEFAULT 0 COMMENT '累计已支付，退款不回补本场次数',
 version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(3) NOT NULL,
 update_time DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,activity_id,generation,member_id),
 CONSTRAINT ck_seckill_member_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND activity_id BETWEEN 1 AND 9007199254740991
  AND member_id BETWEEN 1 AND 9007199254740991 AND generation BETWEEN 1 AND 2147483647),
 CONSTRAINT ck_seckill_member_balance CHECK(held_quantity>=0 AND paid_quantity>=0 AND held_quantity+paid_quantity<=2147483647),
 CONSTRAINT ck_seckill_member_version CHECK(version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原发布代次的持久会员占额/累计支付';

CREATE TABLE promotion_seckill_request (
 tenant_id BIGINT NOT NULL,
 request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 member_id BIGINT NOT NULL,
 client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 activity_version BIGINT NOT NULL,
 redis_epoch BIGINT NOT NULL,
 init_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sku_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 price_cent INT NOT NULL,
 goods_amount BIGINT NOT NULL,
 shipping_amount BIGINT NOT NULL,
 pay_amount BIGINT NOT NULL,
 quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'trade服务端秒杀专用报价引用；不得混用普通报价',
 quote_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quote_expires_time DATETIME(3) NOT NULL,
 reservation_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status TINYINT NOT NULL DEFAULT 10 COMMENT '10受理20已建单30关闭中40失败50未支付取消60待确认；不是支付到账状态',
 order_id BIGINT NULL,
 fence_version BIGINT NOT NULL DEFAULT 0,
 accepted_time DATETIME(3) NOT NULL,
 reconcile_deadline DATETIME(3) NOT NULL COMMENT '受理+2分钟核查目标，不授权超时回补库存',
 pay_deadline DATETIME(3) NOT NULL COMMENT '首次受理+30分钟，后续建单不能续期',
 next_retry_time DATETIME(3) NOT NULL,
 retry_count INT NOT NULL DEFAULT 0,
 claim_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 locked_until DATETIME(3) NULL,
 failure_code INT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(3) NOT NULL,
 update_time DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,request_no),
 UNIQUE KEY uk_seckill_request_client(tenant_id,member_id,client_request_id),
 UNIQUE KEY uk_seckill_request_quote(tenant_id,member_id,quote_id),
 UNIQUE KEY uk_seckill_request_reservation(tenant_id,reservation_token),
 UNIQUE KEY uk_seckill_request_order(tenant_id,order_id),
 KEY idx_seckill_request_member(tenant_id,member_id,accepted_time,request_no),
 KEY idx_seckill_request_activity(tenant_id,activity_id,generation,status,request_no),
 KEY idx_seckill_request_reconcile(tenant_id,status,next_retry_time,locked_until,request_no),
 CONSTRAINT ck_seckill_request_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND member_id BETWEEN 1 AND 9007199254740991
  AND activity_id BETWEEN 1 AND 9007199254740991 AND sku_id BETWEEN 1 AND 9007199254740991 AND generation BETWEEN 1 AND 2147483647
  AND activity_version BETWEEN 0 AND 9007199254740991 AND redis_epoch BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_request_keys CHECK(
  REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(client_request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(quote_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(init_token,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(reservation_token,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_digest,'^[0-9a-f]{64}$','c') AND REGEXP_LIKE(quote_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_seckill_request_amount CHECK(quantity BETWEEN 1 AND 9999 AND price_cent BETWEEN 1 AND 2000000000
  AND goods_amount=quantity*price_cent AND shipping_amount BETWEEN 0 AND 9007199254740991
  AND pay_amount=goods_amount+shipping_amount AND pay_amount BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_request_state CHECK(status IN(10,20,30,40,50,60) AND (order_id IS NULL OR order_id BETWEEN 1 AND 9007199254740991)
  AND (status<>20 OR order_id IS NOT NULL) AND (status<>10 OR order_id IS NULL)),
 CONSTRAINT ck_seckill_request_versions CHECK(fence_version BETWEEN 0 AND 9007199254740991 AND version BETWEEN 0 AND 9007199254740991
  AND retry_count BETWEEN 0 AND 1000000),
 CONSTRAINT ck_seckill_request_time CHECK(accepted_time<quote_expires_time AND reconcile_deadline=TIMESTAMPADD(SECOND,120,accepted_time)
  AND pay_deadline=TIMESTAMPADD(MINUTE,30,accepted_time)),
 CONSTRAINT ck_seckill_request_lease CHECK((claim_token IS NULL AND locked_until IS NULL) OR (claim_token IS NOT NULL AND locked_until IS NOT NULL
  AND REGEXP_LIKE(claim_token,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀持久申请及原报价引用，无明文地址/逻辑删除';

CREATE TABLE promotion_seckill_outbox (
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
 UNIQUE KEY uk_seckill_outbox_aggregate(tenant_id,request_no,aggregate_version,event_type),
 KEY idx_seckill_outbox_delivery(tenant_id,status,available_at,lease_until,event_id),
 CONSTRAINT ck_seckill_outbox_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND aggregate_version BETWEEN 0 AND 9007199254740991
  AND replay_version BETWEEN 0 AND 9007199254740991 AND schema_version=1),
 CONSTRAINT ck_seckill_outbox_keys CHECK(REGEXP_LIKE(event_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
 CONSTRAINT ck_seckill_outbox_state CHECK(event_type IN('SECKILL_CREATE','SECKILL_CLOSE') AND status IN('PENDING','SENDING','PUBLISHED','MANUAL') AND attempts BETWEEN 0 AND 10),
 CONSTRAINT ck_seckill_outbox_lease CHECK((status='SENDING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
  OR (status<>'SENDING' AND lease_owner IS NULL AND lease_until IS NULL)),
 CONSTRAINT ck_seckill_outbox_payload CHECK(JSON_TYPE(payload)='OBJECT' AND JSON_LENGTH(payload)=4
  AND JSON_CONTAINS_PATH(payload,'all','$.schemaVersion','$.tenantId','$.requestNo','$.aggregateVersion')
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.schemaVersion'))='INTEGER' AND JSON_EXTRACT(payload,'$.schemaVersion')=1
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.tenantId'))='INTEGER' AND JSON_EXTRACT(payload,'$.tenantId')=tenant_id
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.requestNo'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.requestNo'))=request_no
  AND JSON_TYPE(JSON_EXTRACT(payload,'$.aggregateVersion'))='INTEGER' AND JSON_EXTRACT(payload,'$.aggregateVersion')=aggregate_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本申请事务的可靠事件引用；尚无发送任务';

ALTER TABLE promotion_seckill_quota_entry
 DROP CHECK ck_seckill_result_entry_type,
 ADD CONSTRAINT ck_seckill_result_entry_type CHECK(event_type IN('ALLOCATE','COMPENSATE','ACCEPT'));
SELECT 'SECKILL_ADMISSION_LEDGER_SCHEMA_READY';
