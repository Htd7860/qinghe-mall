-- DEC-046/076: MySQL JSON positive integers may report UNSIGNED INTEGER.
-- Preserve integer-only and range guards; no business rows or states are rewritten.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE trade_json_integer_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO trade_json_integer_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_trade'
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_trade_outbox_expiry_due' AND CHECK_CLAUSE NOT LIKE '%UNSIGNED INTEGER%')
 AND EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_after_sale_return_address' AND CHECK_CLAUSE NOT LIKE '%UNSIGNED INTEGER%'),1,0);
ALTER TABLE trade_order_outbox DROP CHECK ck_trade_outbox_expiry_due,
 ADD CONSTRAINT ck_trade_outbox_expiry_due CHECK(event_type<>'ORDER_EXPIRE_CHECK' OR (
 aggregate_version=0 AND JSON_CONTAINS_PATH(payload,'one','$.dueAt')=1
 AND JSON_TYPE(JSON_EXTRACT(payload,'$.dueAt')) IN('INTEGER','UNSIGNED INTEGER')
 AND CAST(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.dueAt')) AS UNSIGNED) BETWEEN 1 AND 9007199254740991));
ALTER TABLE trade_after_sale DROP CHECK ck_after_sale_return_address,
 ADD CONSTRAINT ck_after_sale_return_address CHECK(
 (return_address IS NULL AND return_deadline IS NULL)
 OR (return_address IS NOT NULL AND way=20 AND audit_user_id IS NOT NULL AND JSON_TYPE(return_address)='OBJECT'
 AND JSON_CONTAINS_PATH(return_address,'all','$.name','$.mobile','$.areaId','$.detail')=1
 AND JSON_TYPE(JSON_EXTRACT(return_address,'$.name'))='STRING'
 AND CHAR_LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(return_address,'$.name')))) BETWEEN 1 AND 50
 AND JSON_TYPE(JSON_EXTRACT(return_address,'$.mobile'))='STRING'
 AND REGEXP_LIKE(JSON_UNQUOTE(JSON_EXTRACT(return_address,'$.mobile')),'^1[3-9][0-9]{9}$','c')
 AND JSON_TYPE(JSON_EXTRACT(return_address,'$.areaId')) IN('INTEGER','UNSIGNED INTEGER')
 AND CAST(JSON_UNQUOTE(JSON_EXTRACT(return_address,'$.areaId')) AS SIGNED)>0
 AND JSON_TYPE(JSON_EXTRACT(return_address,'$.detail'))='STRING'
 AND CHAR_LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(return_address,'$.detail')))) BETWEEN 1 AND 255));
SELECT 'TRADE_JSON_INTEGER_CHECKS_READY' AS result;
