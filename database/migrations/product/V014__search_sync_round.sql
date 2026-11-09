-- DEC-046: additive local durable search cursor; no historical business DML or ready initialization.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_search_round_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_search_round_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_search_control','product_search_snapshot') AND ENGINE='InnoDB')=2
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_search_round'),1,0);
CREATE TABLE product_search_round (
 tenant_id BIGINT NOT NULL,
 round_no CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 version BIGINT NOT NULL,
 alias_name VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 physical_name VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 index_uuid VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 phase VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 capture_after BIGINT NOT NULL DEFAULT 0,
 send_after BIGINT NOT NULL DEFAULT 0,
 captured_count BIGINT NOT NULL DEFAULT 0,
 sent_count BIGINT NOT NULL DEFAULT 0,
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id),
 CONSTRAINT ck_search_round_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND version BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_search_round_no CHECK(round_no REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
 CONSTRAINT ck_search_round_target CHECK(alias_name REGEXP '^qinghe-product-[a-z0-9][a-z0-9-]{0,63}$'
  AND physical_name REGEXP '^qinghe-product-[a-z0-9][a-z0-9-]{0,111}$' AND physical_name<>alias_name AND index_uuid REGEXP '^[A-Za-z0-9_-]{1,64}$'),
 CONSTRAINT ck_search_round_cursors CHECK(capture_after BETWEEN 0 AND 9007199254740991 AND send_after BETWEEN 0 AND capture_after
  AND captured_count BETWEEN 0 AND capture_after AND sent_count BETWEEN 0 AND captured_count
  AND ((captured_count=0 AND capture_after=0) OR (captured_count>0 AND capture_after>0))
  AND ((sent_count=0 AND send_after=0) OR (sent_count>0 AND send_after>0))),
 CONSTRAINT ck_search_round_phase CHECK(phase IN('CAPTURE','SEND','REFRESH','DONE')
  AND (phase<>'CAPTURE' OR (send_after=0 AND sent_count=0))
  AND (phase NOT IN('REFRESH','DONE') OR (send_after=capture_after AND sent_count=captured_count)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索固定捕获/发送/刷新轮次；进度不代表业务验收';
SELECT 'PRODUCT_SEARCH_ROUND_READY';
