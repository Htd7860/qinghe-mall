-- DEC-046: local qinghe_product only; additive immutable sales snapshots, no business DML.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_search_snapshot_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_search_snapshot_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_spu','product_sku','product_search_control') AND ENGINE='InnoDB')=3
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_search_snapshot'),1,0);
CREATE TABLE product_search_snapshot (
 tenant_id BIGINT NOT NULL,
 version BIGINT NOT NULL COMMENT '原目录change_version；同版本正文不覆盖',
 product_id BIGINT NOT NULL,
 payload MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '固定字段顺序JSON原文，重试不重新读取商品',
 payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,version,product_id),
 CONSTRAINT ck_search_snapshot_identity CHECK(tenant_id BETWEEN 1 AND 9007199254740991 AND product_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_search_snapshot_version CHECK(version BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_search_snapshot_payload CHECK(JSON_VALID(payload) AND OCTET_LENGTH(payload) BETWEEN 2 AND 65536),
 CONSTRAINT ck_search_snapshot_hash CHECK(payload_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品搜索不可变销售快照；无任意覆盖或同步确认入口';
SELECT 'PRODUCT_SEARCH_SNAPSHOT_READY';
