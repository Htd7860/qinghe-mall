-- DEC-046/065/066. Cancel-before-reserve fence; no existing coupon/order data overwritten.
-- Expand the return-reason CHECK only: a coupon may be used just before order creation fails.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE coupon_gate_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO coupon_gate_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('promotion_coupon_order','promotion_coupon_audit','promotion_coupon') AND ENGINE='InnoDB')=3
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_coupon_order_reason')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_coupon_order_gate'),1,0);

CREATE TABLE promotion_coupon_order_gate (
 tenant_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 coupon_id BIGINT NOT NULL,
 closed BIT(1) NOT NULL DEFAULT b'0',
 reason VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,order_id),
 CONSTRAINT ck_coupon_gate_identity CHECK(tenant_id>0 AND order_id>0 AND user_id>0 AND coupon_id>0),
 CONSTRAINT ck_coupon_gate_reason CHECK((closed=b'0' AND reason IS NULL)
  OR (closed=b'1' AND reason IS NOT NULL AND reason IN('CREATE_FAILED','UNPAID_CLOSED','FULL_REFUND'))),
 CONSTRAINT ck_coupon_gate_time CHECK(update_time>=create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE promotion_coupon_order DROP CHECK ck_coupon_order_reason,
 ADD CONSTRAINT ck_coupon_order_reason CHECK((state IN(1,2) AND return_reason IS NULL)
  OR (state=3 AND return_reason IS NOT NULL AND return_reason IN('CREATE_FAILED','UNPAID_CLOSED'))
  OR (state=4 AND return_reason IS NOT NULL AND return_reason IN('CREATE_FAILED','UNPAID_CLOSED','FULL_REFUND')));

-- Seed only the new gate table from committed history. Never reopen a terminal historical order.
INSERT INTO promotion_coupon_order_gate(tenant_id,order_id,user_id,coupon_id,closed,reason)
SELECT tenant_id,order_id,user_id,coupon_id,IF(state IN(3,4),b'1',b'0'),return_reason
FROM promotion_coupon_order;
SELECT 'COUPON_ORDER_CLOSE_GATE_CREATED' AS result;
