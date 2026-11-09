-- DEC-046. Accept original cumulative-return receipts and conserving POOL_RETURNED journal only.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_pool_return_confirmation_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_pool_return_confirmation_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_promotion'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='promotion_seckill_pool_return')=21
 AND NOT EXISTS(SELECT 1 FROM promotion_seckill_pool_return) AND NOT EXISTS(SELECT 1 FROM promotion_seckill_stock_receipt)
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_result_entry_type' AND CHECK_CLAUSE NOT LIKE '%POOL_RETURNED%'),1,0);
ALTER TABLE promotion_seckill_stock_receipt
 MODIFY COLUMN product_status TINYINT NOT NULL COMMENT '10实际划拨20发布补偿30全部归还50部分归还',
 DROP CHECK ck_seckill_result_state,
 ADD CONSTRAINT ck_seckill_result_state CHECK(product_status IN(10,20,30,50) AND allocated_quantity>=0 AND returned_quantity BETWEEN 0 AND allocated_quantity
  AND (product_status<>10 OR (allocated_quantity>0 AND returned_quantity=0))
  AND (product_status<>20 OR returned_quantity=allocated_quantity)
  AND (product_status<>30 OR (allocated_quantity>0 AND returned_quantity=allocated_quantity))
  AND (product_status<>50 OR (allocated_quantity>0 AND returned_quantity>0 AND returned_quantity<allocated_quantity)));
ALTER TABLE promotion_seckill_quota_entry DROP CHECK ck_seckill_result_entry_type,
 ADD CONSTRAINT ck_seckill_result_entry_type CHECK(event_type IN('ALLOCATE','COMPENSATE','ACCEPT','ORDER_CREATED','CREATE_CLOSED','ORDER_PAID','ORDER_CANCELLED','ORDER_REFUND_RESTORED','POOL_RETURNED'));
SELECT 'SECKILL_POOL_RETURN_CONFIRMATION_READY';
