-- DEC-046. Immutable original successful-refund obligation, not a stock-restored receipt.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_refund_record_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_refund_record_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_request')=41
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_refund_record'),1,0);
CREATE TABLE promotion_seckill_refund_record (
 tenant_id BIGINT NOT NULL,
 after_sale_id BIGINT NOT NULL,
 request_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 order_id BIGINT NOT NULL,
 order_item_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 generation INT NOT NULL,
 redis_epoch BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 refund_no CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 refunded_count INT NOT NULL,
 restore_count INT NOT NULL,
 refund_amount BIGINT NOT NULL,
 pay_refund_id BIGINT NULL,
 refund_time DATETIME(3) NOT NULL,
 after_sale_version BIGINT NOT NULL,
 order_version BIGINT NOT NULL,
 shipped TINYINT NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME(3) NOT NULL,
 update_time DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,after_sale_id),
 UNIQUE KEY uk_seckill_refund_number(tenant_id,refund_no),
 KEY idx_seckill_refund_request(tenant_id,request_no,after_sale_id),
 KEY idx_seckill_refund_pending(tenant_id,state,after_sale_id),
 CONSTRAINT ck_seckill_refund_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND after_sale_id BETWEEN 1 AND 9007199254740991
  AND order_id BETWEEN 1 AND 9007199254740991 AND order_item_id BETWEEN 1 AND 9007199254740991 AND member_id BETWEEN 1 AND 9007199254740991
  AND activity_id BETWEEN 1 AND 9007199254740991 AND generation>0 AND redis_epoch BETWEEN 1 AND 9007199254740991 AND sku_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_seckill_refund_keys CHECK(REGEXP_LIKE(request_no,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')
  AND REGEXP_LIKE(refund_no,'^RF[0-9a-f]{32}$','c')),
 CONSTRAINT ck_seckill_refund_counts CHECK(refunded_count BETWEEN 1 AND 10000 AND restore_count BETWEEN 0 AND refunded_count
  AND shipped IN(0,1) AND (shipped=1 OR restore_count=refunded_count)),
 CONSTRAINT ck_seckill_refund_money CHECK(refund_amount BETWEEN 0 AND 9007199254740991
  AND ((refund_amount=0 AND pay_refund_id IS NULL) OR (refund_amount>0 AND pay_refund_id IS NOT NULL AND pay_refund_id BETWEEN 1 AND 9007199254740991))),
 CONSTRAINT ck_seckill_refund_versions CHECK(after_sale_version BETWEEN 0 AND 9007199254740991 AND order_version BETWEEN 0 AND 9007199254740991 AND version BETWEEN 0 AND 9007199254740991),
 CONSTRAINT ck_seckill_refund_state CHECK((restore_count=0 AND state='NOT_REQUIRED') OR (restore_count>0 AND state IN('PENDING','RESTORED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀原成功退款库存义务，不代表已归还';
SELECT 'SECKILL_REFUND_RECORD_READY';
