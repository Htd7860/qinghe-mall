-- DEC-046/065/066. Close only original overdue unsubmitted returns; fixed original-order recovery kind.
-- This migration starts no job and changes no decisions, unit states, counters or money.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_expiry_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO after_sale_expiry_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale')=35
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale_log')=14
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery')=16
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_recovery_replay')=20
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_after_sale_inspection')
 AND (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME IN('ck_trade_recovery_kind','ck_trade_replay_kind') AND CHECK_CLAUSE LIKE '%AUTO_RECEIVE%' AND CHECK_CLAUSE NOT LIKE '%RETURN_EXPIRE%')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale' AND INDEX_NAME='idx_after_sale_deadline'),1,0);
ALTER TABLE trade_after_sale DROP CHECK ck_after_sale_status,
 ADD CONSTRAINT ck_after_sale_status CHECK(status IN(10,20,30,35,40,50,61,62,64) AND way IN(10,20)),
 ADD CONSTRAINT ck_after_sale_expired CHECK(status<>64 OR (way=20 AND audit_user_id IS NOT NULL AND return_deadline IS NOT NULL
  AND return_deadline<=update_time AND delivery_time IS NULL AND receive_time IS NULL)),
 ADD KEY idx_after_sale_deadline(tenant_id,status,return_deadline,order_id,id);
ALTER TABLE trade_after_sale_log DROP CHECK ck_after_sale_log_identity,
 ADD CONSTRAINT ck_after_sale_log_identity CHECK(tenant_id>0 AND after_sale_id>0
  AND ((actor_id>0 AND actor_type IN('MEMBER','ADMIN')) OR (actor_id=0 AND actor_type='SYSTEM' AND action='SYSTEM_RETURN_EXPIRE'))),
 DROP CHECK ck_after_sale_log_transition,
 ADD CONSTRAINT ck_after_sale_log_transition CHECK(
  (action='MEMBER_APPLY' AND from_status IS NULL AND to_status=10 AND after_sale_version=0 AND actor_type='MEMBER' AND request_id IS NULL AND reason='' AND quality_approved=b'0' AND details IS NULL)
  OR (request_id IS NOT NULL AND REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
   AND CHAR_LENGTH(TRIM(reason))>0 AND after_sale_version BETWEEN 1 AND 9007199254740991
   AND ((action='ADMIN_APPROVE' AND actor_type='ADMIN' AND from_status=10 AND to_status IN(20,40))
    OR (action='ADMIN_REJECT' AND actor_type='ADMIN' AND from_status=10 AND to_status=62 AND quality_approved=b'0' AND details IS NULL)
    OR (action='MEMBER_CANCEL' AND actor_type='MEMBER' AND from_status IN(10,20,40) AND to_status=61 AND quality_approved=b'0' AND details IS NULL)
    OR (action='MEMBER_DELIVERY' AND actor_type='MEMBER' AND from_status=20 AND to_status=30 AND quality_approved=b'0' AND details IS NOT NULL)))
  OR (request_id IS NOT NULL AND REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
   AND CHAR_LENGTH(TRIM(reason))>0 AND after_sale_version BETWEEN 1 AND 9007199254740991 AND actor_type='ADMIN' AND quality_approved=b'0' AND details IS NOT NULL
   AND from_status IN(30,35) AND ((action='ADMIN_RECEIVE' AND to_status=40) OR (action='ADMIN_RECEIVE_DISPUTE' AND to_status=35)))
  OR (action='SYSTEM_RETURN_EXPIRE' AND actor_type='SYSTEM' AND actor_id=0 AND from_status=20 AND to_status=64
   AND after_sale_version BETWEEN 1 AND 9007199254740991 AND request_id IS NULL AND reason='买家寄回超期未提交物流' AND quality_approved=b'0' AND details IS NULL));
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
  OR (action='AFTERSALE_CANCEL' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='MEMBER' AND order_version>0 AND details IS NULL)
  OR (action='AFTERSALE_DELIVERY' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='MEMBER' AND order_version>0 AND details IS NULL)
  OR (action='AFTERSALE_INSPECT' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='ADMIN' AND order_version>0 AND details IS NULL)
  OR (action='AFTERSALE_EXPIRE' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='SYSTEM' AND actor_id=0 AND order_version>0 AND details IS NULL));
ALTER TABLE trade_order_recovery DROP CHECK ck_trade_recovery_kind,
 ADD CONSTRAINT ck_trade_recovery_kind CHECK(kind IN('PAY_SYNC','CANCEL_EXPIRED','CANCEL_RELEASE','CREATE_COMPENSATE','AUTO_RECEIVE','RETURN_EXPIRE'));
ALTER TABLE trade_order_recovery_replay DROP CHECK ck_trade_replay_kind,
 ADD CONSTRAINT ck_trade_replay_kind CHECK(kind IN('PAY_SYNC','CANCEL_EXPIRED','CANCEL_RELEASE','CREATE_COMPENSATE','AUTO_RECEIVE','RETURN_EXPIRE'));
SELECT 'AFTER_SALE_RETURN_EXPIRY_CREATED' AS result;
