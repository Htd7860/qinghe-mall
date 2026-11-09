-- REQ-015 / DEC-027. Only product-owned schema; MySQL >= 8.0.16, UTC.
CREATE TABLE product_seckill_allocation (
 id bigint NOT NULL AUTO_INCREMENT COMMENT '实物划拨ID',
 tenant_id bigint NOT NULL COMMENT '租户',
 activity_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'promotion活动号',
 generation int unsigned NOT NULL COMMENT '活动发布代次',
 sku_id bigint NOT NULL COMMENT '本服务商品SKU',
 operation_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次发布稳定业务键',
 payload_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同键异参检查',
 quantity int unsigned NOT NULL COMMENT '划拨量',
 returned_quantity int unsigned NOT NULL DEFAULT 0 COMMENT '累计已归还量',
 status tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0待处理10已划拨20已全部补偿30已结清40待核查',
 version bigint unsigned NOT NULL DEFAULT 0 COMMENT '条件更新版本',
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC',
 update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_allocation_operation(tenant_id,operation_no),
 UNIQUE KEY uk_allocation_sku(tenant_id,activity_no,generation,sku_id),
 KEY idx_allocation_scan(status,update_time,id),
 CONSTRAINT ck_allocation_qty CHECK(quantity > 0 AND returned_quantity <= quantity),
 CONSTRAINT ck_allocation_state CHECK(status IN (0,10,20,30,40))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='普通库存向秒杀池划拨及归还凭证';

CREATE TABLE product_seckill_stock_entry (
 id bigint NOT NULL AUTO_INCREMENT COMMENT '实物库存流水ID',
 tenant_id bigint NOT NULL COMMENT '租户',
 allocation_id bigint NOT NULL COMMENT '本服务划拨逻辑ID',
 sku_id bigint NOT NULL COMMENT '本服务SKU',
 operation_no varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '划拨/分次归还稳定操作键',
 payload_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同键异参检查',
 event_type varchar(40) NOT NULL COMMENT 'ALLOCATE/COMPENSATE/RETURN等枚举',
 quantity int unsigned NOT NULL COMMENT '正数数量，方向由事件类型决定',
 reason varchar(500) NOT NULL DEFAULT '' COMMENT '归还或补偿原因',
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记账UTC，禁止删除',
 PRIMARY KEY(id), UNIQUE KEY uk_stock_operation(tenant_id,operation_no),
 KEY idx_stock_allocation(tenant_id,allocation_id,id),
 CONSTRAINT ck_stock_qty CHECK(quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='与商品库存更新同事务的划拨流水';
