-- DEC-046/065, B04. Current local qinghe_product only. Existing catalog is not modified.
-- V002 is the separate, not-yet-executed seckill script; this script does not apply it.
-- MySQL DDL implicitly commits; inspect partial state before retrying.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_spu_schema_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
INSERT INTO product_spu_schema_guard
SELECT IF(@@hostname='QIANG' AND VERSION() LIKE '8.0.%' AND DATABASE()='qinghe_product'
 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_category','product_brand','product_property','product_property_value') AND ENGINE='InnoDB')=4
 AND NOT EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN('product_spu','product_sku')),1,0);

CREATE TABLE product_spu (
 id BIGINT NOT NULL AUTO_INCREMENT,
 name VARCHAR(128) NOT NULL,
 keyword VARCHAR(255) NOT NULL DEFAULT '',
 introduction VARCHAR(512) NOT NULL DEFAULT '',
 description MEDIUMTEXT NOT NULL,
 category_id BIGINT NOT NULL,
 brand_id BIGINT NOT NULL,
 pic_url VARCHAR(1024) NOT NULL,
 slider_pic_urls JSON NOT NULL,
 sort INT NOT NULL DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 0,
 spec_type BIT(1) NOT NULL DEFAULT b'0',
 price BIGINT NOT NULL DEFAULT 0,
 market_price BIGINT NOT NULL DEFAULT 0,
 cost_price BIGINT NOT NULL DEFAULT 0,
 stock BIGINT NOT NULL DEFAULT 0,
 delivery_template_id BIGINT NOT NULL,
 sales_count BIGINT NOT NULL DEFAULT 0,
 browse_count BIGINT NOT NULL DEFAULT 0,
 auth_version BIGINT NOT NULL DEFAULT 0,
 owner_user_id BIGINT NOT NULL,
 owner_dept_id BIGINT NULL,
 display_properties JSON NOT NULL,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 KEY idx_product_spu_category(tenant_id,category_id,deleted,id),
 KEY idx_product_spu_brand(tenant_id,brand_id,deleted,id),
 KEY idx_product_spu_owner(tenant_id,owner_dept_id,owner_user_id,deleted,id),
 KEY idx_product_spu_status(tenant_id,deleted,status,sort,id),
 CONSTRAINT ck_product_spu_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_spu_name CHECK(CHAR_LENGTH(TRIM(name))>0),
 CONSTRAINT ck_product_spu_references CHECK(category_id>0 AND brand_id>0 AND delivery_template_id>0),
 CONSTRAINT ck_product_spu_status CHECK(status IN(-1,0,1)),
 CONSTRAINT ck_product_spu_sort CHECK(sort BETWEEN 0 AND 10000),
 CONSTRAINT ck_product_spu_amount CHECK(price BETWEEN 0 AND 2147483647 AND market_price BETWEEN 0 AND 2147483647 AND cost_price BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_spu_stock CHECK(stock BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_spu_statistics CHECK(sales_count BETWEEN 0 AND 2147483647 AND browse_count BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_spu_version CHECK(auth_version>=0),
 CONSTRAINT ck_product_spu_owner CHECK(owner_user_id>0 AND (owner_dept_id IS NULL OR owner_dept_id>0)),
 CONSTRAINT ck_product_spu_images CHECK(JSON_TYPE(slider_pic_urls)='ARRAY' AND JSON_LENGTH(slider_pic_urls) BETWEEN 1 AND 20),
 CONSTRAINT ck_product_spu_display CHECK(JSON_TYPE(display_properties)='ARRAY' AND JSON_LENGTH(display_properties)<=100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_sku (
 id BIGINT NOT NULL AUTO_INCREMENT,
 spu_id BIGINT NOT NULL,
 properties JSON NOT NULL,
 price BIGINT NOT NULL,
 market_price BIGINT NOT NULL DEFAULT 0,
 cost_price BIGINT NOT NULL DEFAULT 0,
 bar_code VARCHAR(100) NOT NULL DEFAULT '',
 pic_url VARCHAR(1024) NOT NULL DEFAULT '',
 stock BIGINT NOT NULL DEFAULT 0,
 weight DECIMAL(18,6) NULL COMMENT 'kg; missing value is not interpreted as free delivery',
 volume DECIMAL(18,6) NULL,
 sales_count BIGINT NOT NULL DEFAULT 0,
 status TINYINT NOT NULL DEFAULT 0,
 property_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 auth_version BIGINT NOT NULL DEFAULT 0,
 tenant_id BIGINT NOT NULL,
 creator VARCHAR(64) NOT NULL DEFAULT '',
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updater VARCHAR(64) NOT NULL DEFAULT '',
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 deleted BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(id),
 UNIQUE KEY uk_product_sku_combination(tenant_id,spu_id,property_key),
 KEY idx_product_sku_spu(tenant_id,spu_id,deleted,status,id),
 CONSTRAINT ck_product_sku_tenant CHECK(tenant_id>0),
 CONSTRAINT ck_product_sku_spu CHECK(spu_id>0),
 CONSTRAINT ck_product_sku_price CHECK(price BETWEEN 1 AND 2147483647 AND market_price BETWEEN 0 AND 2147483647 AND cost_price BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_sku_stock CHECK(stock BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_sku_measures CHECK((weight IS NULL OR weight>0) AND (volume IS NULL OR volume>0)),
 CONSTRAINT ck_product_sku_sales CHECK(sales_count BETWEEN 0 AND 2147483647),
 CONSTRAINT ck_product_sku_status CHECK(status IN(0,1)),
 CONSTRAINT ck_product_sku_version CHECK(auth_version>=0),
 CONSTRAINT ck_product_sku_properties CHECK(JSON_TYPE(properties)='ARRAY' AND JSON_LENGTH(properties)<=20),
 CONSTRAINT ck_product_sku_key CHECK(property_key REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT 'PRODUCT_SPU_SKU_SCHEMA_CREATED' AS result;
