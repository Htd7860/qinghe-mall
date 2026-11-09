-- DEC-046/B02: globally unique normalized Host, no domain seeds or verification bypass.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE tenant_entry_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO tenant_entry_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_system'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='system_tenant' AND ENGINE='InnoDB')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='system_tenant_entry'),1,0);
CREATE TABLE system_tenant_entry (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 host VARCHAR(260) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 purpose VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'STOREFRONT',
 kind VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CUSTOM',
 status TINYINT NOT NULL DEFAULT 1,
 binding_version BIGINT NOT NULL DEFAULT 0,
 verified_tenant_id BIGINT NULL,
 verified_binding_version BIGINT NULL,
 domain_verified_at DATETIME(6) NULL,
 https_verified_at DATETIME(6) NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_tenant_entry_host(host),
 KEY idx_tenant_entry_owner(tenant_id,deleted,status,id),
 CONSTRAINT ck_tenant_entry_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_tenant_entry_host CHECK(host=LOWER(host) AND host REGEXP '^[a-z0-9.-]+(:[0-9]{1,5})?$' AND CHAR_LENGTH(host)>0),
 CONSTRAINT ck_tenant_entry_purpose CHECK(purpose IN('STOREFRONT','BACKEND','BOTH')),
 CONSTRAINT ck_tenant_entry_kind CHECK(kind IN('LOCAL','CUSTOM')),
 CONSTRAINT ck_tenant_entry_status CHECK(status IN(0,1)),
 CONSTRAINT ck_tenant_entry_version CHECK(auth_version>=0 AND binding_version>=0),
 CONSTRAINT ck_tenant_entry_proof CHECK((verified_tenant_id IS NULL AND verified_binding_version IS NULL AND domain_verified_at IS NULL AND https_verified_at IS NULL)
  OR (verified_tenant_id>0 AND verified_binding_version>=0 AND verified_tenant_id IS NOT NULL AND verified_binding_version IS NOT NULL AND domain_verified_at IS NOT NULL AND https_verified_at IS NOT NULL)),
 CONSTRAINT ck_tenant_entry_enabled CHECK(status=1 OR kind='LOCAL' OR (verified_tenant_id=tenant_id AND verified_binding_version=binding_version AND domain_verified_at IS NOT NULL AND https_verified_at IS NOT NULL
  AND verified_tenant_id IS NOT NULL AND verified_binding_version IS NOT NULL)),
 CONSTRAINT ck_tenant_entry_host_kind CHECK((kind='LOCAL' AND host REGEXP '^(localhost|127[.]0[.]0[.]1):[0-9]{1,5}$') OR (kind='CUSTOM' AND LOCATE(':',host)=0 AND host NOT IN('localhost','127.0.0.1'))),
 CONSTRAINT ck_tenant_entry_deleted CHECK(deleted=b'0' OR status=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'TENANT_ENTRY_CREATED' AS result;
