-- DEC-046: additive product search progress only; no historical business row is changed.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_search_control_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_search_control_guard SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_catalog_mutex')
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='product_search_control'),1,0);
CREATE TABLE product_search_control (
 tenant_id BIGINT NOT NULL,
 change_version BIGINT NOT NULL DEFAULT 0 COMMENT '同事务商品变更版本；未来同步捕获不可变快照前读取',
 synced_version BIGINT NOT NULL DEFAULT 0 COMMENT '实际索引刷新确认后才可推进，不可等同发送成功',
 initialized BIT(1) NOT NULL DEFAULT b'0' COMMENT '初始扫描及索引校验完成后才true',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id),
 CONSTRAINT ck_search_control_tenant CHECK(tenant_id BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_search_control_version CHECK(change_version BETWEEN 0 AND 9007199254740991
  AND synced_version BETWEEN 0 AND change_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索变更持久进度；未追平不开放ES结果';
SELECT 'PRODUCT_SEARCH_CHANGE_CONTROL_READY';
