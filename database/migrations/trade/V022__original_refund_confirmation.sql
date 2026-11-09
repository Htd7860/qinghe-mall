-- DEC-046/057/065/066. Original refund confirmation; keep funds and stock/coupon facts separate.
-- No channel calls, business confirmations or resource effects performed by this migration.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE refund_confirmation_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO refund_confirmation_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale_refund')=21
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale')=35
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order')=37
 AND NOT EXISTS(SELECT 1 FROM trade_after_sale_refund WHERE status='SUCCESS')
 AND NOT EXISTS(SELECT 1 FROM trade_after_sale WHERE status=50)
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_trade_order_log_transition' AND CHECK_CLAUSE LIKE '%AFTERSALE_REFUND_PREPARE%'
  AND CHECK_CLAUSE NOT LIKE '%AFTERSALE_REFUND_SUCCESS%'),1,0);
ALTER TABLE trade_after_sale_refund
 ADD COLUMN pay_refund_id BIGINT NULL,
 ADD COLUMN pay_refund_version BIGINT NULL,
 ADD COLUMN refund_time DATETIME(6) NULL,
 ADD COLUMN stock_recovered BIT(1) NOT NULL DEFAULT b'0',
 ADD COLUMN coupon_recovered BIT(1) NOT NULL DEFAULT b'0',
 ADD KEY idx_after_sale_refund_resources(tenant_id,status,stock_recovered,coupon_recovered,after_sale_id),
 ADD CONSTRAINT ck_after_sale_refund_confirmation CHECK(
  ((pay_refund_id IS NULL AND pay_refund_version IS NULL)
   OR (pay_refund_id IS NOT NULL AND pay_refund_id BETWEEN 1 AND 9007199254740991 AND pay_refund_version IS NOT NULL AND pay_refund_version BETWEEN 0 AND 9007199254740991))
  AND (total_price>0 OR pay_refund_id IS NULL)
  AND ((status='SUCCESS' AND refund_time IS NOT NULL AND (total_price=0 OR pay_refund_id IS NOT NULL)
    AND refund_time>=create_time-INTERVAL 1 SECOND AND refund_time<=update_time+INTERVAL 1 SECOND
    AND (restore_count<>0 OR stock_recovered=b'1') AND (coupon_return_required=b'1' OR coupon_recovered=b'1'))
   OR (status<>'SUCCESS' AND refund_time IS NULL AND stock_recovered=b'0' AND coupon_recovered=b'0')));
ALTER TABLE trade_after_sale
 ADD COLUMN refund_time DATETIME(6) NULL,
 ADD CONSTRAINT ck_after_sale_confirmation_time CHECK((status=50 AND refund_time IS NOT NULL
  AND refund_time>=create_time-INTERVAL 1 SECOND AND refund_time<=update_time+INTERVAL 1 SECOND)
  OR (status<>50 AND refund_time IS NULL));
ALTER TABLE trade_order DROP CHECK ck_trade_order_cancel,
 ADD CONSTRAINT ck_trade_order_cancel CHECK(
  (status<>40 AND cancel_type IS NULL AND cancel_time IS NULL AND cancel_resources_released=b'0')
  OR (status=40 AND pay_status=b'0' AND cancel_type IS NOT NULL AND cancel_type IN(10,30)
   AND cancel_time IS NOT NULL AND cancel_time>=create_time AND cancel_time<=update_time
   AND (cancel_type<>10 OR cancel_time>=pay_deadline))
  OR (status=40 AND pay_status=b'1' AND cancel_type IS NOT NULL AND cancel_type=20 AND after_sale_pending_count=0 AND cancel_resources_released=b'0'
   AND cancel_time IS NOT NULL AND cancel_time>=pay_time AND cancel_time<=update_time));
ALTER TABLE trade_order DROP CHECK ck_trade_order_fulfillment,
 ADD CONSTRAINT ck_trade_order_fulfillment CHECK(
 ((status IN(0,10) OR (status=40 AND delivery_time IS NULL)) AND logistics_id IS NULL AND logistics_code IS NULL
  AND logistics_name IS NULL AND logistics_no IS NULL AND delivery_time IS NULL AND receive_time IS NULL)
 OR ((status IN(20,30) OR (status=40 AND cancel_type=20)) AND pay_status=b'1'
  AND logistics_id IS NOT NULL AND logistics_id>0 AND logistics_code IS NOT NULL AND REGEXP_LIKE(logistics_code,'^[A-Z0-9_-]{2,32}$','c')
  AND logistics_name IS NOT NULL AND CHAR_LENGTH(TRIM(logistics_name))>0
  AND logistics_no IS NOT NULL AND REGEXP_LIKE(logistics_no,'^[A-Za-z0-9][A-Za-z0-9-]{3,63}$','c')
  AND delivery_time IS NOT NULL AND delivery_time>=create_time AND delivery_time<=update_time
  AND ((status=20 AND receive_time IS NULL)
   OR (status=30 AND receive_time IS NOT NULL AND receive_time>=delivery_time AND receive_time<=update_time)
   OR (status=40 AND (receive_time IS NULL OR (receive_time>=delivery_time AND receive_time<=update_time))))));
ALTER TABLE trade_after_sale_log DROP CHECK ck_after_sale_log_identity,
 ADD CONSTRAINT ck_after_sale_log_identity CHECK(tenant_id>0 AND after_sale_id>0
  AND ((actor_id>0 AND actor_type IN('MEMBER','ADMIN')) OR (actor_id=0 AND actor_type='SYSTEM' AND action IN('SYSTEM_RETURN_EXPIRE','SYSTEM_REFUND_SUCCESS')))),
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
  OR (action IN('SYSTEM_RETURN_EXPIRE','SYSTEM_REFUND_SUCCESS') AND actor_type='SYSTEM' AND actor_id=0 AND from_status=20 AND to_status=64
   AND after_sale_version BETWEEN 1 AND 9007199254740991 AND request_id IS NULL AND reason='买家寄回超期未提交物流' AND quality_approved=b'0' AND details IS NULL));
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
   AND from_status IN(30,35) AND ((action='ADMIN_RECEIVE' AND to_status=40) OR (action='ADMIN_RECEIVE_DISPUTE' AND to_status=35)))
  OR (action='SYSTEM_RETURN_EXPIRE' AND actor_type='SYSTEM' AND actor_id=0 AND from_status=20 AND to_status=64
   AND after_sale_version BETWEEN 1 AND 9007199254740991 AND request_id IS NULL AND reason='买家寄回超期未提交物流' AND quality_approved=b'0' AND details IS NULL)
  OR (action='ADMIN_REFUND_PREPARE' AND actor_type='ADMIN' AND actor_id>0 AND from_status=40 AND to_status=45
   AND request_id IS NOT NULL AND REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
   AND CHAR_LENGTH(TRIM(reason))>0 AND after_sale_version BETWEEN 1 AND 9007199254740991 AND quality_approved=b'0' AND details IS NOT NULL)
  OR (action='SYSTEM_REFUND_SUCCESS' AND actor_type='SYSTEM' AND actor_id=0 AND from_status=45 AND to_status=50
   AND after_sale_version BETWEEN 1 AND 9007199254740991 AND request_id IS NULL AND reason='原退款结果已确认' AND quality_approved=b'0' AND details IS NOT NULL));
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
  OR (action='AFTERSALE_EXPIRE' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='SYSTEM' AND actor_id=0 AND order_version>0 AND details IS NULL)
  OR (action='AFTERSALE_REFUND_PREPARE' AND from_status IN(10,20,30) AND to_status=from_status
   AND actor_type='ADMIN' AND order_version>0 AND details IS NULL)
  OR (action='AFTERSALE_REFUND_SUCCESS' AND actor_type='SYSTEM' AND actor_id=0 AND from_status IN(10,20,30)
   AND (to_status=from_status OR to_status=40) AND order_version>0 AND details IS NULL));
SELECT 'ORIGINAL_REFUND_CONFIRMATION_READY' AS result;
