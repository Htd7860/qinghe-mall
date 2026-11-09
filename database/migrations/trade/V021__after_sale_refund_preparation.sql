-- DEC-046/057/065/066. Freeze original refund identity and original-freight balance before channel execution.
-- No channel, funds confirmation, stock/coupon recovery or business decisions are executed here.
-- Existing nonzero refund balances need a separate reviewed backfill; this migration refuses to infer them.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_refund_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO after_sale_refund_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order')=35
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale')=35
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale_log')=14
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_after_sale_refund')
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order'
  AND COLUMN_NAME IN('delivery_refund_price','delivery_refund_reserved'))
 AND NOT EXISTS(SELECT 1 FROM trade_order WHERE refund_price<>0)
 AND NOT EXISTS(SELECT 1 FROM trade_after_sale_unit WHERE state='REFUNDED')
 AND (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME IN('ck_after_sale_return_shipping','ck_after_sale_inspection','ck_after_sale_return_deadline'))=3
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_after_sale_status' AND CHECK_CLAUSE LIKE '%64%' AND CHECK_CLAUSE NOT LIKE '%45%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_trade_order_log_transition' AND CHECK_CLAUSE LIKE '%AFTERSALE_EXPIRE%' AND CHECK_CLAUSE NOT LIKE '%AFTERSALE_REFUND_PREPARE%'),1,0);
ALTER TABLE trade_order
 ADD COLUMN delivery_refund_price BIGINT NOT NULL DEFAULT 0 COMMENT 'Confirmed original freight refund, integer cents',
 ADD COLUMN delivery_refund_reserved BIGINT NOT NULL DEFAULT 0 COMMENT 'Unconfirmed original freight reservation, integer cents',
 ADD CONSTRAINT ck_trade_order_refund_freight CHECK(delivery_refund_price>=0 AND delivery_refund_reserved>=0
  AND delivery_refund_price+delivery_refund_reserved<=delivery_price AND delivery_refund_price<=refund_price
  AND refund_price+delivery_refund_reserved<=pay_price);
CREATE TABLE trade_after_sale_refund (
 tenant_id BIGINT NOT NULL,
 after_sale_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 pay_order_id BIGINT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 refund_no CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 actor_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 after_sale_version BIGINT NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 goods_price BIGINT NOT NULL,
 delivery_price BIGINT NOT NULL,
 total_price BIGINT NOT NULL,
 restore_count INT NOT NULL,
 coupon_return_required BIT(1) NOT NULL,
 shipped BIT(1) NOT NULL,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 active_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status<>'SUCCESS' THEN order_id ELSE NULL END) STORED,
 PRIMARY KEY(tenant_id,after_sale_id),
 UNIQUE KEY uk_after_sale_refund_no(refund_no),
 UNIQUE KEY uk_after_sale_refund_request(tenant_id,request_id),
 UNIQUE KEY uk_after_sale_refund_active_order(tenant_id,active_order_id),
 KEY idx_after_sale_refund_order(tenant_id,order_id,status),
 CONSTRAINT ck_after_sale_refund_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991
  AND after_sale_id BETWEEN 1 AND 9007199254740991 AND order_id BETWEEN 1 AND 9007199254740991
  AND order_item_id BETWEEN 1 AND 9007199254740991 AND user_id BETWEEN 1 AND 9007199254740991
  AND actor_id BETWEEN 1 AND 9007199254740991 AND (pay_order_id IS NULL OR pay_order_id BETWEEN 1 AND 9007199254740991)),
 CONSTRAINT ck_after_sale_refund_request CHECK(REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(refund_no,'^RF[0-9a-f]{32}$','c') AND CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500),
 CONSTRAINT ck_after_sale_refund_status CHECK(status IN('PREPARED','PROCESSING','UNKNOWN','SUCCESS','FAILED')),
 CONSTRAINT ck_after_sale_refund_money CHECK(goods_price BETWEEN 0 AND 9007199254740991
  AND delivery_price BETWEEN 0 AND 9007199254740991 AND total_price BETWEEN 0 AND 9007199254740991
  AND total_price=goods_price+delivery_price AND (total_price=0 OR pay_order_id IS NOT NULL)),
 CONSTRAINT ck_after_sale_refund_restore CHECK(restore_count BETWEEN 0 AND 9999 AND (shipped=b'1' OR restore_count>0)),
 CONSTRAINT ck_after_sale_refund_version CHECK(after_sale_version BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_after_sale_refund_time CHECK(update_time>=create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE trade_after_sale
 MODIFY COLUMN active_item_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN(10,20,30,35,40,45) THEN order_item_id ELSE NULL END) STORED,
 DROP CHECK ck_after_sale_status,
 ADD CONSTRAINT ck_after_sale_status CHECK(status IN(10,20,30,35,40,45,50,61,62,64) AND way IN(10,20));
ALTER TABLE trade_after_sale DROP CHECK ck_after_sale_return_shipping,
 ADD CONSTRAINT ck_after_sale_return_shipping CHECK(
  (delivery_time IS NULL AND logistics_id IS NULL AND logistics_code IS NULL AND logistics_name IS NULL AND logistics_no IS NULL AND status NOT IN(30,35))
  OR (delivery_time IS NOT NULL AND way=20 AND status IN(30,35,40,45,50) AND return_address IS NOT NULL
   AND audit_time IS NOT NULL AND return_deadline IS NOT NULL AND delivery_time>=audit_time AND delivery_time<return_deadline AND delivery_time<=update_time
   AND logistics_id IS NOT NULL AND logistics_id>0 AND logistics_code IS NOT NULL AND REGEXP_LIKE(logistics_code,'^[A-Z0-9_-]{2,32}$','c')
   AND logistics_name IS NOT NULL AND CHAR_LENGTH(TRIM(logistics_name))>0
   AND logistics_no IS NOT NULL AND REGEXP_LIKE(logistics_no,'^[A-Za-z0-9][A-Za-z0-9-]{3,63}$','c')));
ALTER TABLE trade_after_sale DROP CHECK ck_after_sale_inspection,
 ADD CONSTRAINT ck_after_sale_inspection CHECK(
 (receive_time IS NULL AND receive_user_id IS NULL AND receive_reason IS NULL AND received_count=0 AND salable_count=0
  AND status<>35 AND NOT(way=20 AND status IN(40,45,50)))
 OR (receive_time IS NOT NULL AND receive_user_id IS NOT NULL AND receive_user_id BETWEEN 1 AND 9007199254740991
  AND receive_reason IS NOT NULL AND CHAR_LENGTH(TRIM(receive_reason)) BETWEEN 1 AND 500
  AND way=20 AND delivery_time IS NOT NULL AND receive_time>=delivery_time AND receive_time<=update_time
  AND received_count BETWEEN 0 AND 9999 AND salable_count BETWEEN 0 AND received_count
  AND (status=35 OR (status IN(40,45,50) AND received_count=count))));
ALTER TABLE trade_after_sale DROP CHECK ck_after_sale_return_deadline,
 ADD CONSTRAINT ck_after_sale_return_deadline CHECK(
  (return_deadline IS NULL OR (way=20 AND audit_time IS NOT NULL AND return_deadline=DATE_ADD(audit_time,INTERVAL 7 DAY)))
  AND (status<>20 OR (way=20 AND return_deadline IS NOT NULL))
  AND (status NOT IN(20,40,45,62) OR audit_user_id IS NOT NULL));
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
   AND CHAR_LENGTH(TRIM(reason))>0 AND after_sale_version BETWEEN 1 AND 9007199254740991 AND quality_approved=b'0' AND details IS NOT NULL));
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
   AND actor_type='ADMIN' AND order_version>0 AND details IS NULL));
SELECT 'AFTER_SALE_REFUND_PREPARATION_CREATED' AS result;
