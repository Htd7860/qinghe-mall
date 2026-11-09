-- DEC-046/065: private member login outcomes. No raw mobile, password, code, token or UA.
-- DDL implicitly commits. Inspect objects before any retry; no business rows seeded.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE member_login_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO member_login_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_member'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_user' AND ENGINE='InnoDB')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_login_log'),1,0);
CREATE TABLE member_login_log (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NULL,
 trace_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 peer_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 result VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 credential_version BIGINT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_member_login_trace(tenant_id,trace_id),
 KEY idx_member_login_result(tenant_id,result,create_time,id),
 KEY idx_member_login_user(tenant_id,user_id,create_time,id),
 CONSTRAINT ck_member_login_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_member_login_trace CHECK(trace_id REGEXP '^[0-9a-f]{32}$'),
 CONSTRAINT ck_member_login_subject CHECK(subject_hash REGEXP '^[0-9a-f]{64}$'),
 CONSTRAINT ck_member_login_peer CHECK(peer_hash REGEXP '^[0-9a-f]{64}$'),
 CONSTRAINT ck_member_login_result CHECK(result IN('SUCCESS','BAD_CREDENTIALS','LIMITED','ERROR')),
 CONSTRAINT ck_member_login_identity CHECK(
  (result='SUCCESS' AND user_id IS NOT NULL AND user_id>0 AND credential_version IS NOT NULL AND credential_version>=0) OR
  (result IN('BAD_CREDENTIALS','LIMITED','ERROR') AND user_id IS NULL AND credential_version IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'MEMBER_LOGIN_LOG_CREATED' AS result;
