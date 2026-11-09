-- DEC-046/065, selected yudao member identity/address fields. New local database only.
-- No application accounts, credentials or address data are initialized. DDL implicitly commits.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE member_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO member_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_system'
 AND NOT EXISTS(SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='qinghe_member'),1,0);
CREATE DATABASE qinghe_member CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qinghe_member;

CREATE TABLE member_user (
 id BIGINT NOT NULL AUTO_INCREMENT,
 mobile VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 password VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status TINYINT NOT NULL DEFAULT 0,
 register_ip VARCHAR(45) NOT NULL DEFAULT '',
 register_terminal INT NOT NULL DEFAULT 0,
 login_ip VARCHAR(45) NOT NULL DEFAULT '',
 login_date DATETIME(6) NULL,
 nickname VARCHAR(50) NOT NULL DEFAULT '',
 avatar VARCHAR(1024) NOT NULL DEFAULT '',
 marketing_opt_out BIT(1) NOT NULL DEFAULT b'0',
 auth_version BIGINT NOT NULL DEFAULT 0,
 credential_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_member_user_mobile(tenant_id,mobile),
 KEY idx_member_user_status(tenant_id,deleted,status,id),
 CONSTRAINT ck_member_user_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_member_user_mobile CHECK(mobile REGEXP '^1[3-9][0-9]{9}$'),
 CONSTRAINT ck_member_user_password CHECK(password REGEXP '^[$]2[aby][$][0-9]{2}[$][./A-Za-z0-9]{53}$'),
 CONSTRAINT ck_member_user_status CHECK(status IN(0,1)),
 CONSTRAINT ck_member_user_terminal CHECK(register_terminal>=0),
 CONSTRAINT ck_member_user_auth_version CHECK(auth_version>=0),
 CONSTRAINT ck_member_user_credential_version CHECK(credential_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_address (
 id BIGINT NOT NULL AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 name VARCHAR(50) NOT NULL,
 mobile VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 area_id BIGINT NOT NULL,
 detail_address VARCHAR(255) NOT NULL,
 default_status BIT(1) NOT NULL DEFAULT b'0',
 auth_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 default_user_id BIGINT GENERATED ALWAYS AS(CASE WHEN default_status=b'1' AND deleted=b'0' THEN user_id ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_member_address_default(tenant_id,default_user_id),
 KEY idx_member_address_user(tenant_id,user_id,deleted,default_status,id),
 CONSTRAINT ck_member_address_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_member_address_user CHECK(user_id>0),
 CONSTRAINT ck_member_address_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_member_address_mobile CHECK(mobile REGEXP '^1[3-9][0-9]{9}$'),
 CONSTRAINT ck_member_address_area CHECK(area_id>0 AND area_id<=2147483647),
 CONSTRAINT ck_member_address_detail CHECK(CHAR_LENGTH(TRIM(detail_address))>0),
 CONSTRAINT ck_member_address_version CHECK(auth_version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_address_audit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 address_id BIGINT NOT NULL,
 action VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 KEY idx_member_address_audit_user(tenant_id,user_id,id),
 KEY idx_member_address_audit_address(tenant_id,address_id,id),
 CONSTRAINT ck_member_address_audit_identity CHECK(tenant_id>0 AND user_id>0 AND address_id>0),
 CONSTRAINT ck_member_address_audit_action CHECK(action IN('CREATE','UPDATE','DELETE','DEFAULT_CLEAR')),
 CONSTRAINT ck_member_address_audit_version CHECK(from_version>=-1 AND from_version<9223372036854775807 AND to_version=from_version+1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'MEMBER_SCHEMA_CREATED' AS result;
