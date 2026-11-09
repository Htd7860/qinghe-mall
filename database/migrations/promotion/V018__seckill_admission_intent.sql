-- DEC-046/057/065/066. Durable pre-reservation fence. No business admission, inventory or Redis initialization.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_admission_intent_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_admission_intent_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_admission_intent')
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_request')=34
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_redis_epoch')=14,1,0);
CREATE TABLE promotion_seckill_admission_intent (
 tenant_id BIGINT NOT NULL,
 request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 member_id BIGINT NOT NULL,
 client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 sku_id BIGINT NOT NULL,
 request_snapshot MEDIUMTEXT NOT NULL COMMENT 'verbatim JSON without address; SHA256 uses original UTF8 bytes, not MySQL JSON formatting',
 context_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending / 10 accepted with actual request in same TX / 20 rejected forever',
 failure_code INT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,request_no),
 UNIQUE KEY uk_seckill_intent_client(tenant_id,member_id,client_request_id),
 KEY idx_seckill_intent_activity(tenant_id,activity_id,generation,status,created_at),
 CONSTRAINT ck_seckill_intent_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND member_id BETWEEN 1 AND 9007199254740991
  AND activity_id BETWEEN 1 AND 9007199254740991 AND sku_id BETWEEN 1 AND 9007199254740991 AND generation>0),
 CONSTRAINT ck_seckill_intent_refs CHECK(REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(client_request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(request_digest,'^[0-9a-f]{64}$','c') AND REGEXP_LIKE(context_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_seckill_intent_state CHECK(status IN(0,10,20) AND version BETWEEN 0 AND 9007199254740991
  AND ((status=0 AND version=0) OR (status IN(10,20) AND version>0))
  AND ((status IN(0,10) AND failure_code IS NULL) OR (status=20 AND failure_code IS NOT NULL AND failure_code IN(400,409,429,503)))),
 CONSTRAINT ck_seckill_intent_payload CHECK(OCTET_LENGTH(request_snapshot) BETWEEN 2 AND 16384 AND JSON_VALID(request_snapshot)
  AND JSON_TYPE(CAST(request_snapshot AS JSON))='OBJECT'
  AND JSON_CONTAINS_PATH(request_snapshot,'all','$.tenantId','$.memberId','$.requestNo','$.clientRequestId','$.requestDigest','$.activityId','$.generation','$.skuId','$.quoteId','$.reservationToken')=1
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.tenantId'))=CAST(tenant_id AS CHAR)
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.memberId'))=CAST(member_id AS CHAR)
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.requestNo'))=request_no
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.clientRequestId'))=client_request_id
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.requestDigest'))=request_digest
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.activityId'))=CAST(activity_id AS CHAR)
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.generation'))=CAST(generation AS CHAR)
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.skuId'))=CAST(sku_id AS CHAR)
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.quoteId'))=request_no
  AND JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.reservationToken'))=request_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='original pre-reservation intent; rejected prevents late acceptance';
SELECT 'SECKILL_ADMISSION_INTENT_SCHEMA_READY' AS result;
