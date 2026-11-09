-- DEC-046/065/066. Single-parcel shipment and receipt facts on existing orders.
-- No order/payment/stock business writes, no account or role grants.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE fulfillment_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO fulfillment_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order')=28
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_item')=22
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order_log')=10
 AND (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME IN('ck_trade_order_log_identity','ck_trade_order_log_transition'))=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_delivery_express')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME IN('ck_trade_order_fulfillment','ck_trade_order_after_sale_pending','ck_trade_order_item_fulfillment','ck_trade_order_log_details'))
 AND NOT EXISTS(SELECT 1 FROM trade_order WHERE status IN(20,30) OR refund_price<>0),1,0);
ALTER TABLE trade_order
 ADD COLUMN logistics_id BIGINT NULL,
 ADD COLUMN logistics_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN logistics_name VARCHAR(64) NULL,
 ADD COLUMN logistics_no VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN delivery_time DATETIME(6) NULL,
 ADD COLUMN receive_time DATETIME(6) NULL,
 ADD COLUMN after_sale_pending_count INT NOT NULL DEFAULT 0,
 ADD KEY idx_trade_order_auto_receive(tenant_id,status,after_sale_pending_count,delivery_time,id),
 ADD CONSTRAINT ck_trade_order_after_sale_pending CHECK(after_sale_pending_count BETWEEN 0 AND 200
  AND (after_sale_pending_count=0 OR pay_status=b'1')),
 ADD CONSTRAINT ck_trade_order_fulfillment CHECK(
  (status IN(0,10,40) AND logistics_id IS NULL AND logistics_code IS NULL AND logistics_name IS NULL
   AND logistics_no IS NULL AND delivery_time IS NULL AND receive_time IS NULL)
  OR (status IN(20,30) AND pay_status=b'1' AND logistics_id IS NOT NULL AND logistics_id>0
   AND logistics_code IS NOT NULL AND REGEXP_LIKE(logistics_code,'^[A-Z0-9_-]{2,32}$','c')
   AND logistics_name IS NOT NULL AND CHAR_LENGTH(TRIM(logistics_name))>0
   AND logistics_no IS NOT NULL AND REGEXP_LIKE(logistics_no,'^[A-Za-z0-9][A-Za-z0-9-]{3,63}$','c')
   AND delivery_time IS NOT NULL AND delivery_time>=create_time AND delivery_time<=update_time
   AND ((status=20 AND receive_time IS NULL) OR (status=30 AND receive_time IS NOT NULL AND receive_time>=delivery_time AND receive_time<=update_time))));
ALTER TABLE trade_order_item
 ADD COLUMN refund_count INT NOT NULL DEFAULT 0,
 ADD COLUMN delivery_count INT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_trade_order_item_fulfillment CHECK(refund_count BETWEEN 0 AND count AND delivery_count BETWEEN 0 AND count);
ALTER TABLE trade_order_log
 ADD COLUMN details JSON NULL,
 DROP CHECK ck_trade_order_log_identity,
 ADD CONSTRAINT ck_trade_order_log_identity CHECK(tenant_id>0 AND order_id>0 AND order_version BETWEEN 0 AND 9007199254740991
  AND ((actor_type IN('MEMBER','ADMIN') AND actor_id>0) OR (actor_type='SYSTEM' AND actor_id=0))),
 ADD CONSTRAINT ck_trade_order_log_details CHECK(details IS NULL OR JSON_TYPE(details)='OBJECT'),
 DROP CHECK ck_trade_order_log_transition,
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
  OR (action='SYSTEM_RECEIVE' AND from_status=20 AND to_status=30 AND order_version>0 AND actor_type='SYSTEM'));
CREATE TABLE trade_delivery_express (
 id BIGINT NOT NULL AUTO_INCREMENT,
 code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 name VARCHAR(64) NOT NULL,
 logo VARCHAR(2048) NULL,
 sort INT NOT NULL DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 creator VARCHAR(64) NOT NULL,
 updater VARCHAR(64) NOT NULL,
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_trade_carrier_code(code),
 KEY idx_trade_carrier_enabled(status,deleted,sort,id),
 CONSTRAINT ck_trade_carrier_code CHECK(REGEXP_LIKE(code,'^[A-Z0-9_-]{2,32}$','c')),
 CONSTRAINT ck_trade_carrier_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_trade_carrier_state CHECK(status IN(0,1) AND sort>=0),
 CONSTRAINT ck_trade_carrier_time CHECK(update_time>=create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Project reference metadata only. Codes are local identifiers, not a claimed external tracking API mapping.
INSERT INTO trade_delivery_express(id,code,name,sort,creator,updater) VALUES
 (1,'SF','顺丰速运',10,'sql-fulfillment-bootstrap','sql-fulfillment-bootstrap'),
 (2,'ZTO','中通快递',20,'sql-fulfillment-bootstrap','sql-fulfillment-bootstrap'),
 (3,'YTO','圆通速递',30,'sql-fulfillment-bootstrap','sql-fulfillment-bootstrap'),
 (4,'EMS','EMS',40,'sql-fulfillment-bootstrap','sql-fulfillment-bootstrap');
SELECT 'ORDINARY_ORDER_FULFILLMENT_SCHEMA_CREATED' AS result;
