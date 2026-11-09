-- DEC-046/065. Same-transaction successful delivery configuration audit. No template/rate seeds.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE trade_delivery_audit_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO trade_delivery_audit_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND ENGINE='InnoDB'
  AND TABLE_NAME IN('trade_delivery_express_template','trade_delivery_express_template_charge','trade_delivery_express_template_free'))=3
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_delivery_template_audit'),1,0);
CREATE TABLE trade_delivery_template_audit (
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
 KEY idx_trade_delivery_audit_tenant(tenant_id,create_time,id),
 KEY idx_trade_delivery_audit_target(tenant_id,target_id,id),
 CONSTRAINT ck_trade_delivery_audit_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_trade_delivery_audit_actor CHECK(actor_user_id>0),
 CONSTRAINT ck_trade_delivery_audit_target CHECK(target_id>0),
 CONSTRAINT ck_trade_delivery_audit_action CHECK(action IN('DELIVERY.CREATE','DELIVERY.UPDATE','DELIVERY.DELETE')),
 CONSTRAINT ck_trade_delivery_audit_version CHECK(from_version>=-1 AND from_version<9223372036854775807 AND to_version=from_version+1),
 CONSTRAINT ck_trade_delivery_audit_details CHECK(JSON_TYPE(details)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'TRADE_DELIVERY_AUDIT_CREATED' AS result;
