-- DEC-046/057/065/066. Original-number refund ledger and parent payment balance reservation.
-- No refund, channel, business result, application/channel configuration or credentials are initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE payment_refund_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO payment_refund_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_pay'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order')=16
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order_extension')=15
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_refund')
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order' AND COLUMN_NAME='refund_reserved')
 AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order' AND COLUMN_NAME='closing')
 AND NOT EXISTS(SELECT 1 FROM pay_order WHERE refund_price<>0),1,0);
ALTER TABLE pay_order
 ADD COLUMN refund_reserved BIGINT NOT NULL DEFAULT 0 COMMENT 'Unconfirmed original refund reservation, integer cents',
 ADD CONSTRAINT ck_pay_order_refund_reserved CHECK(refund_reserved>=0 AND refund_price+refund_reserved<=price
  AND (refund_reserved=0 OR (status IN(10,20) AND closing=b'0')));
CREATE TABLE pay_refund (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 app_id BIGINT NOT NULL,
 channel_id BIGINT NOT NULL,
 channel_version BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 order_no CHAR(33) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 channel_order_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 user_id BIGINT NOT NULL,
 user_type TINYINT NOT NULL,
 merchant_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 merchant_refund_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 no CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 pay_price BIGINT NOT NULL,
 refund_price BIGINT NOT NULL,
 reason VARCHAR(200) NOT NULL,
 channel_refund_no VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
 success_time DATETIME(6) NULL,
 submitted_time DATETIME(6) NULL,
 last_submit_time DATETIME(6) NULL,
 submit_count TINYINT NOT NULL DEFAULT 0,
 auth_version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 active_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status<>'SUCCESS' THEN order_id ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_pay_refund_business(tenant_id,app_id,merchant_refund_id),
 UNIQUE KEY uk_pay_refund_no(no),
 UNIQUE KEY uk_pay_refund_active_order(tenant_id,active_order_id),
 KEY idx_pay_refund_order(tenant_id,order_id,status),
 KEY idx_pay_refund_pending(tenant_id,status,id),
 CONSTRAINT ck_pay_refund_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND app_id BETWEEN 1 AND 9007199254740991
  AND channel_id BETWEEN 1 AND 9007199254740991 AND order_id BETWEEN 1 AND 9007199254740991
  AND user_id BETWEEN 1 AND 9007199254740991 AND user_type=1),
 CONSTRAINT ck_pay_refund_business CHECK(REGEXP_LIKE(merchant_order_id,'^[1-9][0-9]{0,15}$','c')
  AND CAST(merchant_order_id AS UNSIGNED)<=9007199254740991
  AND REGEXP_LIKE(merchant_refund_id,'^[1-9][0-9]{0,15}$','c') AND CAST(merchant_refund_id AS UNSIGNED)<=9007199254740991
  AND REGEXP_LIKE(no,'^RF[0-9a-f]{32}$','c') AND REGEXP_LIKE(order_no,'^P[0-9a-f]{32}$','c')
  AND REGEXP_LIKE(channel_order_no,'^[A-Za-z0-9_-]{1,128}$','c')
  AND (channel_refund_no IS NULL OR REGEXP_LIKE(channel_refund_no,'^[A-Za-z0-9_-]{1,128}$','c'))),
 CONSTRAINT ck_pay_refund_money CHECK(pay_price BETWEEN 1 AND 9007199254740991 AND refund_price BETWEEN 1 AND pay_price),
 CONSTRAINT ck_pay_refund_reason CHECK(CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 200),
 CONSTRAINT ck_pay_refund_version CHECK(auth_version BETWEEN 0 AND 9007199254740991 AND channel_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_pay_refund_state CHECK(status IN('PREPARED','PROCESSING','UNKNOWN','SUCCESS','FAILED')
  AND ((status='PREPARED' AND submit_count=0 AND submitted_time IS NULL AND last_submit_time IS NULL AND channel_refund_no IS NULL)
   OR (status<>'PREPARED' AND submit_count BETWEEN 1 AND 3 AND submitted_time IS NOT NULL AND last_submit_time IS NOT NULL))
  AND ((status='SUCCESS' AND success_time IS NOT NULL) OR (status<>'SUCCESS' AND success_time IS NULL))),
 CONSTRAINT ck_pay_refund_time CHECK(update_time>=create_time
  AND (submitted_time IS NULL OR submitted_time>=create_time)
  AND (last_submit_time IS NULL OR (last_submit_time>=submitted_time AND last_submit_time<=update_time))
  AND (success_time IS NULL OR (success_time>=submitted_time-INTERVAL 1 SECOND AND success_time<=update_time+INTERVAL 1 SECOND)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'ORIGINAL_PAYMENT_REFUND_LEDGER_CREATED' AS result;
