-- DEC-046/065: additive permanent coupon/order receipt and state audit. No account/business seed.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE coupon_order_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO coupon_order_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('promotion_coupon','promotion_coupon_template') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM promotion_coupon WHERE status IN(2,4) OR use_order_id IS NOT NULL OR locked_order_id IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('promotion_coupon_order','promotion_coupon_audit')),1,0);
CREATE TABLE promotion_coupon_order (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 coupon_id BIGINT NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state TINYINT NOT NULL,
 quote_json JSON NOT NULL,
 coupon_version BIGINT NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 return_reason VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_coupon_order(tenant_id,order_id),
 KEY idx_coupon_order_coupon(tenant_id,coupon_id,id),
 KEY idx_coupon_order_user(tenant_id,user_id,id),
 CONSTRAINT ck_coupon_order_identity CHECK(tenant_id>0 AND user_id>0 AND order_id>0 AND coupon_id>0),
 CONSTRAINT ck_coupon_order_digest CHECK(REGEXP_LIKE(request_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_coupon_order_state CHECK(state IN(1,2,3,4)),
 CONSTRAINT ck_coupon_order_versions CHECK(coupon_version>=0 AND auth_version>=0),
 CONSTRAINT ck_coupon_order_quote CHECK(JSON_TYPE(quote_json)='OBJECT'),
 CONSTRAINT ck_coupon_order_reason CHECK((state IN(1,2) AND return_reason IS NULL)
   OR (state=3 AND return_reason IS NOT NULL AND return_reason IN('CREATE_FAILED','UNPAID_CLOSED'))
   OR (state=4 AND return_reason IS NOT NULL AND return_reason IN('UNPAID_CLOSED','FULL_REFUND')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE promotion_coupon_audit (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 coupon_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 order_id BIGINT NULL,
 action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 from_status TINYINT NOT NULL,
 to_status TINYINT NOT NULL,
 from_version BIGINT NOT NULL,
 to_version BIGINT NOT NULL,
 actor_id BIGINT NULL,
 reason VARCHAR(200) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_coupon_audit_version(tenant_id,coupon_id,to_version),
 KEY idx_coupon_audit_order(tenant_id,order_id,id),
 CONSTRAINT ck_coupon_audit_identity CHECK(tenant_id>0 AND coupon_id>0 AND user_id>0 AND (order_id IS NULL OR order_id>0) AND (actor_id IS NULL OR actor_id>0)),
 CONSTRAINT ck_coupon_audit_version CHECK(from_version>=0 AND from_version<9223372036854775807 AND to_version=from_version+1),
 CONSTRAINT ck_coupon_audit_reason CHECK(CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 200),
 CONSTRAINT ck_coupon_audit_transition CHECK(
   (action='HOLD' AND from_status=1 AND to_status=4 AND order_id IS NOT NULL AND actor_id IS NULL)
   OR (action='USE' AND from_status=4 AND to_status=2 AND order_id IS NOT NULL AND actor_id IS NULL)
   OR (action='RELEASE' AND from_status IN(4,5) AND to_status IN(1,3,5) AND order_id IS NOT NULL AND actor_id IS NULL)
   OR (action='RETURN' AND from_status IN(2,5) AND to_status IN(1,3,5) AND order_id IS NOT NULL AND actor_id IS NULL)
   OR (action='INVALIDATE' AND from_status IN(1,2,3,4) AND to_status=5 AND order_id IS NULL AND actor_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'COUPON_ORDER_BINDING_AUDIT_CREATED' AS result;
