-- DEC-046/065/066. Review evidence and immutable command receipts on existing logs.
-- No funds execution, unit release or business decisions performed by this migration.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_decision_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO after_sale_decision_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale')=19
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale_log')=10
 AND NOT EXISTS(SELECT 1 FROM trade_after_sale WHERE status<>10)
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_log_transition'
  AND CHECK_CLAUSE LIKE '%AFTERSALE_APPLY%' AND CHECK_CLAUSE NOT LIKE '%AFTERSALE_APPROVE%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_outbox_type'
  AND CHECK_CLAUSE LIKE '%AFTERSALE_APPLIED%' AND CHECK_CLAUSE NOT LIKE '%AFTERSALE_DECIDED%'),1,0);
ALTER TABLE trade_after_sale
 ADD COLUMN audit_user_id BIGINT NULL,
 ADD COLUMN audit_time DATETIME(6) NULL,
 ADD COLUMN audit_reason VARCHAR(500) NULL,
 ADD COLUMN quality_approved BIT(1) NOT NULL DEFAULT b'0',
 ADD COLUMN return_deadline DATETIME(6) NULL,
 ADD CONSTRAINT ck_after_sale_audit CHECK(
  (audit_user_id IS NULL AND audit_time IS NULL AND audit_reason IS NULL AND quality_approved=b'0')
  OR (audit_user_id IS NOT NULL AND audit_user_id>0 AND audit_time IS NOT NULL AND audit_time>=create_time AND audit_time<=update_time
   AND audit_reason IS NOT NULL AND CHAR_LENGTH(TRIM(audit_reason))>0)),
 ADD CONSTRAINT ck_after_sale_return_deadline CHECK(
  (return_deadline IS NULL OR (way=20 AND audit_time IS NOT NULL AND return_deadline=DATE_ADD(audit_time,INTERVAL 7 DAY)))
  AND (status<>20 OR (way=20 AND return_deadline IS NOT NULL))
  AND (status NOT IN(20,40,62) OR audit_user_id IS NOT NULL));
ALTER TABLE trade_after_sale_log
 ADD COLUMN request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN reason VARCHAR(500) NOT NULL DEFAULT '',
 ADD COLUMN quality_approved BIT(1) NOT NULL DEFAULT b'0',
 ADD UNIQUE KEY uk_after_sale_decision_request(tenant_id,request_id),
 DROP CHECK ck_after_sale_log_identity,
 ADD CONSTRAINT ck_after_sale_log_identity CHECK(tenant_id>0 AND after_sale_id>0 AND actor_id>0 AND actor_type IN('MEMBER','ADMIN')),
 DROP CHECK ck_after_sale_log_transition,
 ADD CONSTRAINT ck_after_sale_log_transition CHECK(
  (action='MEMBER_APPLY' AND from_status IS NULL AND to_status=10 AND after_sale_version=0 AND actor_type='MEMBER' AND request_id IS NULL AND reason='' AND quality_approved=b'0')
  OR (request_id IS NOT NULL AND REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
   AND CHAR_LENGTH(TRIM(reason))>0 AND after_sale_version BETWEEN 1 AND 9007199254740991
   AND ((action='ADMIN_APPROVE' AND actor_type='ADMIN' AND from_status=10 AND to_status IN(20,40))
    OR (action='ADMIN_REJECT' AND actor_type='ADMIN' AND from_status=10 AND to_status=62 AND quality_approved=b'0')
    OR (action='MEMBER_CANCEL' AND actor_type='MEMBER' AND from_status IN(10,20,40) AND to_status=61 AND quality_approved=b'0'))));
-- Existing order transitions are appended below from the V016 source; no old action is removed.
ALTER TABLE trade_order_log DROP CHECK ck_trade_order_log_transition,
 ADD CONSTRAINT ck_trade_order_log_transition CHECK(
  (action='CREATE' AND from_status IS NULL AND to_status=0 AND order_version=0 AND actor_type='MEMBER')
  OR (action='PAY_BIND' AND from_status=0 AND to_status=0 AND order_version>0 AND actor_type='MEMBER')
  OR (action='FREE_PAY' AND from_status=0 AND to_status=10 AND order_version>0 AND actor_type='MEMBER')
  OR (action='PAY_CONFIRM' AND from_status=0 AND to_status=10 AND order_version>0 AND actor_type='SYSTEM')
  OR (action='MEMBER_CANCEL' AND from_status=0 AND to_status=40 AND order_version>0 AND actor_type='MEMBER')
  OR (action='TIMEOUT_CANCEL' AND from_status=0 AND to_status=40 AND order_version>0 AND actor_type='SYSTEM')
  OR (action='CANCEL_RELEASED' AND from_status=40 AND to_status=40 AND order_version>0 AND actor_type='SYSTEM')
  OR (action='ADMIN_DELIVERY' AND from_status=10 AND to_status=20 AND order_version>0 AND actor_type='ADMIN' AND details IS NOT NULL)
  OR (action='ADMIN_LOGISTICS_UPDATE' AND from_status IN(20,30) AND to_status=from_status AND order_version>0 AND actor_type='ADMIN' AND details IS NOT NULL)
  OR (action='MEMBER_RECEIVE' AND from_status=20 AND to_status=30 AND order_version>0 AND actor_type='MEMBER')
  OR (action='SYSTEM_RECEIVE' AND from_status=20 AND to_status=30 AND order_version>0 AND actor_type='SYSTEM')
  OR (action='AFTERSALE_APPLY' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='MEMBER' AND order_version>0 AND details IS NULL)
  OR (action IN('AFTERSALE_APPROVE','AFTERSALE_REJECT') AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='ADMIN' AND order_version>0 AND details IS NULL)
  OR (action='AFTERSALE_CANCEL' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='MEMBER' AND order_version>0 AND details IS NULL));
ALTER TABLE trade_order_outbox DROP CHECK ck_trade_order_outbox_type,
 ADD CONSTRAINT ck_trade_order_outbox_type CHECK(event_type IN('ORDER_PREPARED','ORDER_DELIVERED','ORDER_RECEIVED','AFTERSALE_APPLIED','AFTERSALE_DECIDED'));
SELECT 'AFTER_SALE_DECISION_SCHEMA_CREATED' AS result;
