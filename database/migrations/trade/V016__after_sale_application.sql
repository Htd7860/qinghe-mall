-- DEC-046/065/066. Quantity-based application and original-unit reservations only.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO after_sale_schema_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('trade_after_sale','trade_after_sale_unit','trade_after_sale_log'))
 AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='trade_order' AND COLUMN_NAME='after_sale_pending_count')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_log_transition' AND CHECK_CLAUSE NOT LIKE '%AFTERSALE_APPLY%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_order_outbox_type' AND CHECK_CLAUSE LIKE '%ORDER_RECEIVED%' AND CHECK_CLAUSE NOT LIKE '%AFTERSALE_APPLIED%'),1,0);
CREATE TABLE trade_after_sale (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 no CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 order_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 order_version BIGINT NOT NULL,
 status TINYINT NOT NULL,
 way TINYINT NOT NULL,
 count INT NOT NULL,
 goods_refund_price BIGINT NOT NULL,
 original_delivery_share BIGINT NOT NULL,
 apply_reason VARCHAR(200) NOT NULL,
 apply_description VARCHAR(500) NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(6) NOT NULL,
 update_time DATETIME(6) NOT NULL,
 active_item_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN(10,20,30,40) THEN order_item_id ELSE NULL END) STORED,
 PRIMARY KEY(id),
 UNIQUE KEY uk_after_sale_request(tenant_id,user_id,request_id),
 UNIQUE KEY uk_after_sale_no(no),
 UNIQUE KEY uk_after_sale_active_item(tenant_id,active_item_id),
 KEY idx_after_sale_member(tenant_id,user_id,id),
 KEY idx_after_sale_order(tenant_id,order_id,id),
 CONSTRAINT ck_after_sale_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND user_id BETWEEN 1 AND 9007199254740991 AND order_id>0 AND order_item_id>0),
 CONSTRAINT ck_after_sale_request CHECK(REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c') AND REGEXP_LIKE(no,'^AS[0-9a-f]{32}$','c')),
 CONSTRAINT ck_after_sale_status CHECK(status IN(10,20,30,40,50,61,62) AND way IN(10,20)),
 CONSTRAINT ck_after_sale_price CHECK(count BETWEEN 1 AND 9999 AND goods_refund_price BETWEEN 0 AND 9007199254740991 AND original_delivery_share BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_after_sale_reason CHECK(CHAR_LENGTH(TRIM(apply_reason)) BETWEEN 1 AND 200),
 CONSTRAINT ck_after_sale_version CHECK(order_version BETWEEN 0 AND 9007199254740991 AND auth_version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_after_sale_time CHECK(update_time>=create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE trade_after_sale_unit (
 tenant_id BIGINT NOT NULL,
 after_sale_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 unit_no INT NOT NULL,
 goods_price BIGINT NOT NULL,
 original_delivery_share BIGINT NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 active_item_id BIGINT GENERATED ALWAYS AS (CASE WHEN state IN('RESERVED','REFUNDED') THEN order_item_id ELSE NULL END) STORED,
 PRIMARY KEY(tenant_id,after_sale_id,unit_no),
 UNIQUE KEY uk_after_sale_unit_occupied(tenant_id,active_item_id,unit_no),
 KEY idx_after_sale_unit_order(tenant_id,order_id,state,order_item_id,unit_no),
 CONSTRAINT ck_after_sale_unit_identity CHECK(tenant_id>0 AND after_sale_id>0 AND order_id>0 AND order_item_id>0 AND unit_no BETWEEN 0 AND 9998),
 CONSTRAINT ck_after_sale_unit_price CHECK(goods_price BETWEEN 0 AND 2000000000 AND original_delivery_share BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_after_sale_unit_state CHECK(state IN('RESERVED','REFUNDED','RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE trade_after_sale_log (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 after_sale_id BIGINT NOT NULL,
 actor_id BIGINT NOT NULL,
 actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 from_status TINYINT NULL,
 to_status TINYINT NOT NULL,
 after_sale_version BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_after_sale_log_version(tenant_id,after_sale_id,after_sale_version),
 CONSTRAINT ck_after_sale_log_identity CHECK(tenant_id>0 AND after_sale_id>0 AND actor_id>0 AND actor_type='MEMBER'),
 CONSTRAINT ck_after_sale_log_transition CHECK(action='MEMBER_APPLY' AND from_status IS NULL AND to_status=10 AND after_sale_version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Preserve all eleven current order transitions and append one application action.
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
  OR (action='AFTERSALE_APPLY' AND from_status IN(10,20,30) AND to_status=from_status AND actor_type='MEMBER' AND order_version>0 AND details IS NULL));
ALTER TABLE trade_order_outbox DROP CHECK ck_trade_order_outbox_type,
 ADD CONSTRAINT ck_trade_order_outbox_type CHECK(event_type IN('ORDER_PREPARED','ORDER_DELIVERED','ORDER_RECEIVED','AFTERSALE_APPLIED'));
SELECT 'AFTER_SALE_APPLICATION_SCHEMA_CREATED' AS result;
