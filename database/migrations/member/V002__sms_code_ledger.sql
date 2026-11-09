-- DEC-046/065: tenant-bound SMS reservation/state/one-time consumption, no codes or quotas seeded.
-- DDL implicitly commits; inspect objects after any partial error before retrying.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE member_sms_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO member_sms_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_member'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='member_user' AND ENGINE='InnoDB')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('member_sms_subject','member_sms_code')),1,0);
CREATE TABLE member_sms_subject (
 tenant_id BIGINT NOT NULL,
 mobile VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quota_date DATE NOT NULL,
 daily_count INT NOT NULL DEFAULT 0,
 last_sent_at DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,mobile),
 KEY idx_member_sms_subject_time(update_time,tenant_id,mobile),
 CONSTRAINT ck_member_sms_subject_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_member_sms_subject_mobile CHECK(mobile REGEXP '^1[3-9][0-9]{9}$'),
 CONSTRAINT ck_member_sms_subject_count CHECK(daily_count>=0 AND daily_count<=100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE member_sms_code (
 id BIGINT NOT NULL AUTO_INCREMENT,
 mobile VARCHAR(11) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scene VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_mode VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 code_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 failed_attempts INT NOT NULL DEFAULT 0,
 today_index INT NOT NULL,
 expire_time DATETIME(6) NOT NULL,
 used_time DATETIME(6) NULL,
 api_request_id VARCHAR(128) NULL,
 serial_no VARCHAR(128) NULL,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 KEY idx_member_sms_code_latest(tenant_id,mobile,scene,id),
 KEY idx_member_sms_code_expire(tenant_id,expire_time,id),
 CONSTRAINT ck_member_sms_code_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_member_sms_code_mobile CHECK(mobile REGEXP '^1[3-9][0-9]{9}$'),
 CONSTRAINT ck_member_sms_code_scene CHECK(scene IN('REGISTER','RESET_PASSWORD')),
 CONSTRAINT ck_member_sms_code_provider CHECK(provider_mode IN('TEST','ALIYUN')),
 CONSTRAINT ck_member_sms_code_state CHECK(state IN('PENDING','ACTIVE','UNKNOWN','USED','EXPIRED','LOCKED')),
 CONSTRAINT ck_member_sms_code_hash CHECK(code_hash REGEXP '^[$]2[aby][$][0-9]{2}[$][./A-Za-z0-9]{53}$'),
 CONSTRAINT ck_member_sms_code_attempts CHECK(failed_attempts>=0 AND failed_attempts<=10),
 CONSTRAINT ck_member_sms_code_today CHECK(today_index>0 AND today_index<=100),
 CONSTRAINT ck_member_sms_code_expire CHECK(expire_time>create_time),
 CONSTRAINT ck_member_sms_code_used CHECK((state='USED' AND used_time IS NOT NULL AND used_time>=create_time) OR (state<>'USED' AND used_time IS NULL)),
 CONSTRAINT ck_member_sms_code_receipt_pair CHECK((api_request_id IS NULL AND serial_no IS NULL) OR (api_request_id IS NOT NULL AND serial_no IS NOT NULL)),
 CONSTRAINT ck_member_sms_code_accepted CHECK(state NOT IN('ACTIVE','USED','LOCKED') OR (api_request_id IS NOT NULL AND serial_no IS NOT NULL AND CHAR_LENGTH(api_request_id)>0 AND CHAR_LENGTH(serial_no)>0)),
 CONSTRAINT ck_member_sms_code_locked CHECK(state<>'LOCKED' OR failed_attempts>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SELECT 'MEMBER_SMS_LEDGER_CREATED' AS result;
