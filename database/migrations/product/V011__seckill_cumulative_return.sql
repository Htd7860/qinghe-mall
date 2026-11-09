-- DEC-046. Preserve REVIEW40; RETURNING50 is distinct from manual investigation.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_cumulative_return_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO seckill_cumulative_return_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_seckill_allocation')=17
 AND NOT EXISTS(SELECT 1 FROM product_seckill_allocation) AND NOT EXISTS(SELECT 1 FROM product_seckill_stock_entry)
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND CONSTRAINT_NAME='ck_seckill_product_state' AND CHECK_CLAUSE NOT LIKE '%50%'),1,0);
ALTER TABLE product_seckill_allocation
 MODIFY COLUMN status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待处理10已划拨20已补偿30已全部归还40待核查50部分归还',
 DROP CHECK ck_seckill_product_state,
 ADD CONSTRAINT ck_seckill_product_state CHECK(status IN(0,10,20,30,40,50)
  AND (status<>0 OR (allocated_quantity=0 AND returned_quantity=0))
  AND (status<>10 OR (allocated_quantity=quantity AND returned_quantity=0))
  AND (status<>20 OR returned_quantity=allocated_quantity)
  AND (status<>30 OR (allocated_quantity=quantity AND returned_quantity=allocated_quantity))
  AND (status<>50 OR (allocated_quantity=quantity AND returned_quantity>0 AND returned_quantity<allocated_quantity)));
SELECT 'SECKILL_CUMULATIVE_RETURN_READY';
