-- DEC-046/065. Register conservative business references before product commits.
-- No automatic unbind on timeout/rollback: another service's result can be unknown.
-- Later reconciliation must confirm authoritative resource state before releasing obsolete references.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE infra_reference_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO infra_reference_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_infra'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='infra_file' AND ENGINE='InnoDB')=1
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='infra_file_reference'),1,0);

CREATE TABLE infra_file_reference (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 file_id BIGINT NOT NULL,
 resource_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 resource_id BIGINT NOT NULL,
 actor_user_id BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_infra_file_reference(tenant_id,file_id,resource_type,resource_id),
 KEY idx_infra_file_resource(tenant_id,resource_type,resource_id,file_id),
 CONSTRAINT ck_infra_file_reference_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_infra_file_reference_file CHECK(file_id>0),
 CONSTRAINT ck_infra_file_reference_resource CHECK(resource_id>0 AND resource_type IN('CATEGORY','BRAND','SPU')),
 CONSTRAINT ck_infra_file_reference_actor CHECK(actor_user_id>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'INFRA_FILE_REFERENCE_CREATED' AS result;
