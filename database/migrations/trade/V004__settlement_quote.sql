-- DEC-046/065 B05: successful server quote + immutable request receipt; no quote/order/PII seeds.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE settlement_quote_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO settlement_quote_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('trade_cart','trade_cart_user','trade_delivery_express_template') AND ENGINE='InnoDB')=3
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_settlement_quote'),1,0);
CREATE TABLE trade_settlement_quote (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 quote_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 cart_version BIGINT NOT NULL,
 address_id BIGINT NOT NULL,
 coupon_id BIGINT NULL,
 snapshot JSON NULL,
 created_at DATETIME(6) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 bound_order_id BIGINT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_settlement_quote_id(quote_id),
 UNIQUE KEY uk_settlement_request(tenant_id,user_id,request_id),
 UNIQUE KEY uk_settlement_bound_order(tenant_id,bound_order_id),
 KEY idx_settlement_owner_expiry(tenant_id,user_id,expires_at,id),
 CONSTRAINT ck_settlement_identity CHECK(tenant_id>0 AND user_id>0 AND address_id>0 AND (coupon_id IS NULL OR coupon_id>0) AND (bound_order_id IS NULL OR bound_order_id>0)),
 CONSTRAINT ck_settlement_version CHECK(cart_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_settlement_keys CHECK(REGEXP_LIKE(quote_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
   AND REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c') AND REGEXP_LIKE(request_digest,'^[0-9a-f]{64}$','c')),
 CONSTRAINT ck_settlement_time CHECK(created_at<expires_at AND expires_at<=DATE_ADD(created_at,INTERVAL 900 SECOND)),
 CONSTRAINT ck_settlement_snapshot CHECK((snapshot IS NULL AND bound_order_id IS NULL) OR (snapshot IS NOT NULL AND JSON_TYPE(snapshot)='OBJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'SETTLEMENT_QUOTE_SCHEMA_CREATED' AS result;
