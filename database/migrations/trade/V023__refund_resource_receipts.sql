-- DEC-046/057/065/066. Preserve separate trusted stock/coupon receipts and immutable completion logs.
-- No resource call, financial result, business flags or receipt data is initialized.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE refund_resources_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO refund_resources_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale_refund')=26
 AND NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_after_sale_resource_receipts')
 AND NOT EXISTS(SELECT 1 FROM trade_after_sale_refund WHERE (stock_recovered=b'1' AND restore_count>0) OR (coupon_recovered=b'1' AND coupon_return_required=b'1'))
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_after_sale_log_transition' AND CHECK_CLAUSE LIKE '%SYSTEM_REFUND_SUCCESS%' AND CHECK_CLAUSE NOT LIKE '%SYSTEM_STOCK_RETURN%'),1,0);
ALTER TABLE trade_after_sale_refund
 ADD COLUMN stock_receipt JSON NULL,
 ADD COLUMN coupon_receipt JSON NULL,
 ADD CONSTRAINT ck_after_sale_resource_receipts CHECK(
  ((stock_recovered=b'1' AND restore_count>0 AND stock_receipt IS NOT NULL AND JSON_TYPE(stock_receipt)='OBJECT')
   OR ((stock_recovered=b'0' OR restore_count=0) AND stock_receipt IS NULL))
  AND ((coupon_recovered=b'1' AND coupon_return_required=b'1' AND coupon_receipt IS NOT NULL AND JSON_TYPE(coupon_receipt)='OBJECT')
   OR ((coupon_recovered=b'0' OR coupon_return_required=b'0') AND coupon_receipt IS NULL)));
ALTER TABLE trade_after_sale_log DROP CHECK ck_after_sale_log_identity,
 ADD CONSTRAINT ck_after_sale_log_identity CHECK(tenant_id>0 AND after_sale_id>0
  AND ((actor_id>0 AND actor_type IN('MEMBER','ADMIN')) OR (actor_id=0 AND actor_type='SYSTEM' AND action IN('SYSTEM_RETURN_EXPIRE','SYSTEM_REFUND_SUCCESS','SYSTEM_STOCK_RETURN','SYSTEM_COUPON_RETURN')))),
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
  OR (action IN('SYSTEM_RETURN_EXPIRE','SYSTEM_REFUND_SUCCESS','SYSTEM_STOCK_RETURN','SYSTEM_COUPON_RETURN') AND actor_type='SYSTEM' AND actor_id=0 AND from_status=20 AND to_status=64
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
  OR (action IN('SYSTEM_RETURN_EXPIRE','SYSTEM_REFUND_SUCCESS') AND actor_type='SYSTEM' AND actor_id=0 AND from_status=20 AND to_status=64
   AND after_sale_version BETWEEN 1 AND 9007199254740991 AND request_id IS NULL AND reason='买家寄回超期未提交物流' AND quality_approved=b'0' AND details IS NULL)
  OR (actor_type='SYSTEM' AND actor_id=0 AND from_status=50 AND to_status=50 AND after_sale_version BETWEEN 1 AND 9007199254740991
   AND request_id IS NULL AND quality_approved=b'0' AND details IS NOT NULL
   AND ((action='SYSTEM_STOCK_RETURN' AND reason='原售后库存恢复已确认') OR (action='SYSTEM_COUPON_RETURN' AND reason='原订单退券处理已确认'))));
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
   AND (to_status=from_status OR to_status=40) AND order_version>0 AND details IS NULL)
  OR (action IN('AFTERSALE_STOCK_RETURN','AFTERSALE_COUPON_RETURN') AND actor_type='SYSTEM' AND actor_id=0
   AND from_status IN(10,20,30,40) AND to_status=from_status AND order_version>0 AND details IS NULL));
SELECT 'REFUND_RESOURCE_RECEIPTS_READY' AS result;
