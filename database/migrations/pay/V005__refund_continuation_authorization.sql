-- DEC-046/057/065/066. One audited original-number submission opportunity per manual UUID.
-- No grant/submit/refund/financial result is initialized. Historical attempts are preserved.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE refund_continuation_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO refund_continuation_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_pay'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_refund')=26
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_refund_continuation')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_pay_refund_state'),1,0);
ALTER TABLE pay_refund
 MODIFY COLUMN submit_count INT NOT NULL DEFAULT 0,
 ADD COLUMN submission_limit INT NOT NULL DEFAULT 3,
 DROP CHECK ck_pay_refund_state,
 ADD CONSTRAINT ck_pay_refund_state CHECK(status IN('PREPARED','PROCESSING','UNKNOWN','SUCCESS','FAILED')
  AND submission_limit BETWEEN 3 AND 2147483647 AND submit_count BETWEEN 0 AND submission_limit
  AND ((status='PREPARED' AND submit_count=0 AND submitted_time IS NULL AND last_submit_time IS NULL AND channel_refund_no IS NULL)
   OR (status<>'PREPARED' AND submit_count>=1 AND submitted_time IS NOT NULL AND last_submit_time IS NOT NULL))
  AND ((status='SUCCESS' AND success_time IS NOT NULL) OR (status<>'SUCCESS' AND success_time IS NULL)));
CREATE TABLE pay_refund_continuation (
 tenant_id BIGINT NOT NULL,
 refund_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 from_version BIGINT NOT NULL,
 from_limit INT NOT NULL,
 to_limit INT NOT NULL,
 create_time DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,request_id),
 UNIQUE KEY uk_refund_continuation_limit(tenant_id,refund_id,to_limit),
 CONSTRAINT ck_refund_continuation_owner CHECK(tenant_id BETWEEN 1 AND 9007199254740991
  AND refund_id BETWEEN 1 AND 9007199254740991 AND actor_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_refund_continuation_request CHECK(REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')),
 CONSTRAINT ck_refund_continuation_reason CHECK(CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500),
 CONSTRAINT ck_refund_continuation_budget CHECK(from_version BETWEEN 0 AND 9007199254740990
  AND from_limit BETWEEN 3 AND 2147483646 AND to_limit=from_limit+1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'REFUND_CONTINUATION_AUTHORIZATION_READY' AS result;
