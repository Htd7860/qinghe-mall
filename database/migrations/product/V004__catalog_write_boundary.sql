-- DEC-022/046/065. Only local qinghe_product: low-frequency catalog mutex and success audit.
-- No tenant/role/product records initialized. Runtime mutex row is created per trusted tenant.
-- DDL implicitly commits; inspect partial state before retrying.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_catalog_boundary_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_catalog_boundary_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_category','product_brand','product_property','product_property_value','product_spu','product_sku') AND ENGINE='InnoDB')=6
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_catalog_mutex','product_catalog_audit')),1,0);

CREATE TABLE product_catalog_mutex (
 tenant_id BIGINT NOT NULL,
 PRIMARY KEY(tenant_id),
 CONSTRAINT ck_product_catalog_mutex_tenant CHECK(tenant_id>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_catalog_audit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 actor_user_id BIGINT NOT NULL,
 action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_id BIGINT NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 details JSON NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 KEY idx_product_catalog_audit_tenant(tenant_id,create_time,id),
 KEY idx_product_catalog_audit_target(tenant_id,action,target_id,id),
 CONSTRAINT ck_product_catalog_audit_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_catalog_audit_actor CHECK(actor_user_id>0),
 CONSTRAINT ck_product_catalog_audit_target CHECK(target_id>0),
 CONSTRAINT ck_product_catalog_audit_action CHECK(action REGEXP '^(CATEGORY|BRAND|PROPERTY|VALUE|SPU|SKU)[.](CREATE|UPDATE|DELETE|STATUS|STOCK)$'),
 CONSTRAINT ck_product_catalog_audit_version CHECK(from_version>=-1 AND from_version<9223372036854775807 AND to_version=from_version+1),
 CONSTRAINT ck_product_catalog_audit_details CHECK(JSON_TYPE(details)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'PRODUCT_CATALOG_BOUNDARY_CREATED' AS result;
