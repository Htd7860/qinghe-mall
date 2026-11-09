-- DEC-046/065/066. Bounded explicit recovery of the same payment attempt, no new transaction number.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE qr_recovery_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO qr_recovery_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_pay'
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order_extension')=13
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order_extension' AND ENGINE='InnoDB')=1
 AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pay_order_extension' AND COLUMN_NAME IN('qr_recovery_count','qr_recovery_after'))
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='chk_pay_attempt_qr_recovery'),1,0);
ALTER TABLE pay_order_extension
 ADD COLUMN qr_recovery_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
 ADD COLUMN qr_recovery_after DATETIME(6) NULL,
 ADD CONSTRAINT chk_pay_attempt_qr_recovery CHECK(qr_recovery_count BETWEEN 0 AND 3
  AND ((qr_recovery_count=0 AND qr_recovery_after IS NULL)
   OR (qr_recovery_count>0 AND qr_recovery_after IS NOT NULL AND qr_recovery_after>=create_time)));
SELECT 'ORIGINAL_QR_RECOVERY_BOUNDS_ADDED' AS result;
