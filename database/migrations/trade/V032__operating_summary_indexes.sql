-- DEC-046/057/065/066. Three local event-time aggregate access paths; no reporting table or business data changes.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE operating_summary_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO operating_summary_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN('trade_order','trade_after_sale_refund') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
  AND INDEX_NAME IN('idx_trade_order_created_summary','idx_trade_order_paid_summary','idx_after_sale_refund_success_summary')),1,0);
ALTER TABLE trade_order
 ADD KEY idx_trade_order_created_summary(tenant_id,create_time,status),
 ADD KEY idx_trade_order_paid_summary(tenant_id,pay_status,pay_time,status);
ALTER TABLE trade_after_sale_refund
 ADD KEY idx_after_sale_refund_success_summary(tenant_id,status,refund_time,order_id);
SELECT 'OPERATING_SUMMARY_INDEXES_READY' AS result;
