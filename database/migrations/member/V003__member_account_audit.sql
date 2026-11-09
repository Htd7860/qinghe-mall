-- DEC-046/065. Successful account mutations only; no passwords, phone numbers or SMS codes.
-- DDL implicitly commits. Inspect existing objects before any retry.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE member_account_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO member_account_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_member'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME IN('member_user','member_sms_subject','member_sms_code') AND ENGINE='InnoDB')=3
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_account_audit'),1,0);
CREATE TABLE member_account_audit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 from_auth_version BIGINT NOT NULL,
 to_auth_version BIGINT NOT NULL,
 from_credential_version BIGINT NOT NULL,
 to_credential_version BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 KEY idx_member_account_audit_user(tenant_id,user_id,id),
 UNIQUE KEY uk_member_account_audit_version(tenant_id,user_id,action,to_credential_version),
 CONSTRAINT ck_member_account_audit_identity CHECK(tenant_id>0 AND user_id>0),
 CONSTRAINT ck_member_account_audit_action CHECK(action IN('REGISTER','PASSWORD_RESET')),
 CONSTRAINT ck_member_account_audit_auth CHECK(
  (action='REGISTER' AND from_auth_version=-1 AND to_auth_version=0) OR
  (action='PASSWORD_RESET' AND from_auth_version>=0 AND from_auth_version<9223372036854775807 AND to_auth_version=from_auth_version+1)),
 CONSTRAINT ck_member_account_audit_credential CHECK(
  (action='REGISTER' AND from_credential_version=-1 AND to_credential_version=0) OR
  (action='PASSWORD_RESET' AND from_credential_version>=0 AND from_credential_version<9223372036854775807 AND to_credential_version=from_credential_version+1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'MEMBER_ACCOUNT_AUDIT_CREATED' AS result;
