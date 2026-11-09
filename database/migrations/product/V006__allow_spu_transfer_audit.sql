-- DEC-046: retain all current audit actions and add only SPU.TRANSFER.
-- ALTER is one atomic MySQL8 DDL statement; do not claim transaction rollback for DDL.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_transfer_audit_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_transfer_audit_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_catalog_audit' AND ENGINE='InnoDB')
 AND EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='product_catalog_audit' AND CONSTRAINT_NAME='ck_product_catalog_audit_action' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_product_catalog_audit_action_v2'),1,0);
ALTER TABLE product_catalog_audit
 DROP CHECK ck_product_catalog_audit_action,
 ADD CONSTRAINT ck_product_catalog_audit_action_v2 CHECK(
  action REGEXP '^(CATEGORY|BRAND|PROPERTY|VALUE|SPU|SKU)[.](CREATE|UPDATE|DELETE|STATUS|STOCK)$'
  OR action='SPU.TRANSFER');
SELECT 'PRODUCT_TRANSFER_AUDIT_CONSTRAINT_UPDATED' AS result;
