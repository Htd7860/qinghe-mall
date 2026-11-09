-- DEC-046/065/066. Return inspection facts and active dispute state; no funds or stock execution.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_inspection_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO after_sale_inspection_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale')=30
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale_log')=14
 AND NOT EXISTS(SELECT 1 FROM trade_after_sale WHERE way=20 AND status IN(40,50))
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_log_transition'
  AND CHECK_CLAUSE LIKE '%AFTERSALE_DELIVERY%' AND CHECK_CLAUSE NOT LIKE '%AFTERSALE_INSPECT%'),1,0);
ALTER TABLE trade_after_sale
 ADD COLUMN receive_time DATETIME(6) NULL,
 ADD COLUMN receive_user_id BIGINT NULL,
 ADD COLUMN received_count INT NOT NULL DEFAULT 0,
 ADD COLUMN salable_count INT NOT NULL DEFAULT 0,
 ADD COLUMN receive_reason VARCHAR(500) NULL,
 MODIFY COLUMN active_item_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN(10,20,30,35,40) THEN order_item_id ELSE NULL END) STORED,
 DROP CHECK ck_after_sale_status,
 ADD CONSTRAINT ck_after_sale_status CHECK(status IN(10,20,30,35,40,50,61,62) AND way IN(10,20)),
 DROP CHECK ck_after_sale_return_shipping,
 ADD CONSTRAINT ck_after_sale_return_shipping CHECK(
  (delivery_time IS NULL AND logistics_id IS NULL AND logistics_code IS NULL AND logistics_name IS NULL AND logistics_no IS NULL AND status NOT IN(30,35))
  OR (delivery_time IS NOT NULL AND way=20 AND status IN(30,35,40,50) AND return_address IS NOT NULL
   AND audit_time IS NOT NULL AND return_deadline IS NOT NULL AND delivery_time>=audit_time AND delivery_time<return_deadline AND delivery_time<=update_time
   AND logistics_id IS NOT NULL AND logistics_id>0 AND logistics_code IS NOT NULL AND REGEXP_LIKE(logistics_code,'^[A-Z0-9_-]{2,32}$','c')
   AND logistics_name IS NOT NULL AND CHAR_LENGTH(TRIM(logistics_name))>0
   AND logistics_no IS NOT NULL AND REGEXP_LIKE(logistics_no,'^[A-Za-z0-9][A-Za-z0-9-]{3,63}$','c')));
ALTER TABLE trade_after_sale ADD CONSTRAINT ck_after_sale_inspection CHECK(
 (receive_time IS NULL AND receive_user_id IS NULL AND receive_reason IS NULL AND received_count=0 AND salable_count=0
  AND status<>35 AND NOT(way=20 AND status IN(40,50)))
 OR (receive_time IS NOT NULL AND receive_user_id IS NOT NULL AND receive_user_id BETWEEN 1 AND 9007199254740991
  AND receive_reason IS NOT NULL AND CHAR_LENGTH(TRIM(receive_reason)) BETWEEN 1 AND 500
  AND way=20 AND delivery_time IS NOT NULL AND receive_time>=delivery_time AND receive_time<=update_time
  AND received_count BETWEEN 0 AND 9999 AND salable_count BETWEEN 0 AND received_count
  AND (status=35 OR (status IN(40,50) AND received_count=count))));
ALTER TABLE trade_after_sale_log DROP CHECK ck_after_sale_log_transition,
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
   AND from_status IN(30,35) AND ((action='ADMIN_RECEIVE' AND to_status=40) OR (action='ADMIN_RECEIVE_DISPUTE' AND to_status=35))));
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
  OR (action='AFTERSALE_INSPECT' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='ADMIN' AND order_version>0 AND details IS NULL));
SELECT 'AFTER_SALE_INSPECTION_CREATED' AS result;
