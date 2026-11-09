-- DEC-046/065. Additive template configuration audit only; no grant or account seeds.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE coupon_template_audit_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO coupon_template_audit_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('promotion_coupon_template','promotion_coupon') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_coupon_template_audit'),1,0);
CREATE TABLE promotion_coupon_template_audit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 template_id BIGINT NOT NULL,
 action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 details JSON NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_coupon_template_audit_version(tenant_id,template_id,to_version),
 KEY idx_coupon_template_audit_actor(tenant_id,actor_id,id),
 CONSTRAINT ck_coupon_template_audit_identity CHECK(tenant_id>0 AND actor_id>0 AND template_id>0),
 CONSTRAINT ck_coupon_template_audit_action CHECK(action IN('CREATE','UPDATE','STATUS','DELETE')),
 CONSTRAINT ck_coupon_template_audit_version CHECK((action='CREATE' AND from_version=-1 AND to_version=0) OR (action IN('UPDATE','STATUS','DELETE') AND from_version>=0 AND from_version<9223372036854775807 AND to_version=from_version+1)),
 CONSTRAINT ck_coupon_template_audit_details CHECK(JSON_TYPE(details)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'COUPON_TEMPLATE_AUDIT_CREATED' AS result;
