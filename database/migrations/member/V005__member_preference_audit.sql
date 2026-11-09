-- DEC-046/065. Additive marketing-choice audit, no account/contact/credential content.
-- DDL commits implicitly; inspect existing objects before any retry.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE member_preference_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO member_preference_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_member'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='member_user' AND ENGINE='InnoDB')=1
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_preference_audit'),1,0);
CREATE TABLE member_preference_audit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 from_opt_out BIT(1) NOT NULL,
 to_opt_out BIT(1) NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_member_preference_version(tenant_id,user_id,to_version),
 KEY idx_member_preference_user(tenant_id,user_id,id),
 CONSTRAINT ck_member_preference_identity CHECK(tenant_id>0 AND user_id>0),
 CONSTRAINT ck_member_preference_change CHECK(from_opt_out<>to_opt_out),
 CONSTRAINT ck_member_preference_version CHECK(from_version>=0 AND from_version<9223372036854775807 AND to_version=from_version+1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'MEMBER_PREFERENCE_AUDIT_CREATED' AS result;
