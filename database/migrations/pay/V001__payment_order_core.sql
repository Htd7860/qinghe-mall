-- DEC-046/065/066. New isolated payment database/app/order core; no channel/account/business seeds.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE payment_core_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO payment_core_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%'
 AND NOT EXISTS(SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='qinghe_pay'),1,0);
CREATE DATABASE qinghe_pay CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qinghe_pay;
CREATE TABLE pay_app (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 app_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(64) NOT NULL,
 status TINYINT NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id),
 UNIQUE KEY uk_pay_app_key(tenant_id,app_key),
 CONSTRAINT ck_pay_app_identity CHECK(tenant_id>0),
 CONSTRAINT ck_pay_app_key CHECK(REGEXP_LIKE(app_key,'^[a-z0-9-]{3,64}$','c') AND CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_pay_app_status CHECK(status IN(0,1) AND auth_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_pay_app_time CHECK(update_time>=create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE pay_order (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 app_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 user_type TINYINT NOT NULL,
 merchant_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 subject VARCHAR(32) NOT NULL,
 price BIGINT NOT NULL,
 status TINYINT NOT NULL,
 expire_time DATETIME(6) NOT NULL,
 success_time DATETIME(6) NULL,
 refund_price BIGINT NOT NULL DEFAULT 0,
 auth_version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_pay_order_business(tenant_id,app_id,merchant_order_id),
 KEY idx_pay_order_deadline(tenant_id,status,expire_time,id),
 KEY idx_pay_order_user(tenant_id,user_id,id),
 CONSTRAINT ck_pay_order_identity CHECK(tenant_id>0 AND app_id>0 AND user_id>0 AND user_type=1),
 CONSTRAINT ck_pay_order_business CHECK(REGEXP_LIKE(merchant_order_id,'^[1-9][0-9]{0,15}$','c')
  AND CAST(merchant_order_id AS UNSIGNED)<=9007199254740991 AND CHAR_LENGTH(TRIM(subject))>0),
 CONSTRAINT ck_pay_order_money CHECK(price BETWEEN 1 AND 9007199254740991 AND refund_price BETWEEN 0 AND price),
 CONSTRAINT ck_pay_order_version CHECK(auth_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_pay_order_state CHECK((status IN(0,30) AND success_time IS NULL AND refund_price=0)
  OR (status=10 AND success_time IS NOT NULL) OR (status=20 AND success_time IS NOT NULL AND refund_price>0)),
 CONSTRAINT ck_pay_order_time CHECK(update_time>=create_time AND (expire_time>create_time OR status=30)
  AND (success_time IS NULL OR success_time>=create_time))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'PAYMENT_ORDER_CORE_CREATED' AS result;
