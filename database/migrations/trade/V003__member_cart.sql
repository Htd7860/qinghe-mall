-- DEC-046/065 B05: member cart aggregate and active SKU uniqueness; no account/cart seeds.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE member_cart_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO member_cart_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_delivery_express_template' AND ENGINE='InnoDB')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('trade_cart','trade_cart_user')),1,0);
CREATE TABLE trade_cart_user (
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,user_id),
 CONSTRAINT ck_cart_user_identity CHECK(tenant_id>0 AND user_id>0),
 CONSTRAINT ck_cart_user_version CHECK(auth_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE trade_cart (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 spu_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 count INT NOT NULL,
 selected BIT(1) NOT NULL DEFAULT b'1',
 auth_version BIGINT NOT NULL DEFAULT 0,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 updater VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 active_sku_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=b'0' THEN sku_id ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_cart_active_sku(tenant_id,user_id,active_sku_id),
 KEY idx_cart_user(tenant_id,user_id,deleted,id),
 CONSTRAINT ck_cart_identity CHECK(tenant_id>0 AND user_id>0 AND spu_id>0 AND sku_id>0),
 CONSTRAINT ck_cart_count CHECK(count BETWEEN 1 AND 9999),
 CONSTRAINT ck_cart_version CHECK(auth_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'MEMBER_CART_SCHEMA_CREATED' AS result;
