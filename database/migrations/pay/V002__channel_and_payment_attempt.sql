-- DEC-046/065/066. One stable channel attempt per payment; no app/channel/secrets seeds.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE payment_attempt_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO payment_attempt_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_pay'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('pay_app','pay_order') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('pay_channel','pay_order_extension'))
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order' AND COLUMN_NAME='closing'),1,0);
ALTER TABLE pay_order ADD COLUMN closing BIT(1) NOT NULL DEFAULT b'0' AFTER status,
 DROP CHECK ck_pay_order_time,
 ADD CONSTRAINT ck_pay_order_time CHECK(update_time>=create_time AND (expire_time>create_time OR status=30)
  AND (success_time IS NULL OR success_time>=create_time-INTERVAL 1 SECOND));
CREATE TABLE pay_channel (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 app_id BIGINT NOT NULL,
 code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status TINYINT NOT NULL,
 config_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 alipay_app_id CHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 seller_id CHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 notify_url VARCHAR(2048) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_pay_channel_app(tenant_id,app_id,code),
 UNIQUE KEY uk_pay_channel_config(config_ref),
 CONSTRAINT ck_pay_channel_identity CHECK(tenant_id>0 AND app_id>0),
 CONSTRAINT ck_pay_channel_type CHECK(code='alipay_qr' AND status IN(0,1) AND auth_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_pay_channel_ref CHECK(REGEXP_LIKE(config_ref,'^[a-z0-9-]{3,64}$','c')),
 CONSTRAINT ck_pay_channel_alipay CHECK(REGEXP_LIKE(alipay_app_id,'^[0-9]{16}$','c') AND REGEXP_LIKE(seller_id,'^[0-9]{16}$','c')),
 CONSTRAINT ck_pay_channel_notify CHECK(LEFT(notify_url,8)='https://' AND CHAR_LENGTH(notify_url) BETWEEN 12 AND 2048)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE pay_order_extension (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 channel_id BIGINT NOT NULL,
 channel_version BIGINT NOT NULL,
 no CHAR(33) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 display_content VARCHAR(4096) NULL,
 channel_order_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
 success_time DATETIME(6) NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_pay_extension_order(tenant_id,order_id),
 UNIQUE KEY uk_pay_extension_no(no),
 UNIQUE KEY uk_pay_extension_channel_tx(channel_id,channel_order_no),
 KEY idx_pay_extension_state(tenant_id,state,update_time,id),
 CONSTRAINT ck_pay_extension_identity CHECK(tenant_id>0 AND order_id>0 AND channel_id>0),
 CONSTRAINT ck_pay_extension_versions CHECK(channel_version BETWEEN 0 AND 9007199254740991 AND auth_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_pay_extension_no CHECK(REGEXP_LIKE(no,'^P[0-9a-f]{32}$','c')),
 CONSTRAINT ck_pay_extension_state CHECK((state IN('SUBMITTING','WAITING','CLOSING','CLOSED') AND success_time IS NULL AND channel_order_no IS NULL)
  OR (state IN('SUCCESS','REVIEW') AND success_time IS NOT NULL AND channel_order_no IS NOT NULL)),
 CONSTRAINT ck_pay_extension_channel_no CHECK(channel_order_no IS NULL OR REGEXP_LIKE(channel_order_no,'^[A-Za-z0-9_-]{1,128}$','c')),
 CONSTRAINT ck_pay_extension_time CHECK(update_time>=create_time AND (success_time IS NULL OR success_time>=create_time-INTERVAL 1 SECOND))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'PAYMENT_CHANNEL_ATTEMPT_CREATED' AS result;
