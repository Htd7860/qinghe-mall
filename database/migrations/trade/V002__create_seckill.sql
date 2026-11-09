-- REQ-015 / DEC-027. Only trade-owned schema; MySQL >= 8.0.16, UTC.
CREATE TABLE trade_seckill_order_binding (
 id bigint NOT NULL AUTO_INCREMENT COMMENT '建单栅栏ID',
 tenant_id bigint NOT NULL COMMENT '租户',
 request_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'promotion申请号，关闭先到也保留墓碑',
 payload_hash char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '完整建单参数摘要，先到关闭时可空',
 activity_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '活动号',
 activity_version bigint unsigned NOT NULL COMMENT '受理版本',
 member_id bigint NOT NULL COMMENT '会员',
 sku_id bigint NOT NULL COMMENT '活动SKU',
 quantity int unsigned NOT NULL COMMENT '购买量',
 price_cent bigint unsigned NOT NULL COMMENT '活动单价分',
 shipping_cent bigint unsigned NOT NULL COMMENT '运费分',
 total_cent bigint unsigned NOT NULL COMMENT '订单应付分',
 order_id bigint DEFAULT NULL COMMENT '本服务订单，创建同事务绑定，关闭先到为空',
 state tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0可建单10已建单20关闭墓碑30待确认',
 fence_version bigint unsigned NOT NULL DEFAULT 0 COMMENT '关闭与创建串行裁决版本',
 close_reason varchar(500) NOT NULL DEFAULT '' COMMENT '关闭原因，迟到创建不得复活',
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC',
 update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_binding_request(tenant_id,request_no),
 UNIQUE KEY uk_binding_order(tenant_id,order_id),
 KEY idx_binding_scan(state,update_time,id),
 CONSTRAINT ck_binding_amount CHECK(quantity > 0 AND price_cent > 0 AND total_cent = price_cent * quantity + shipping_cent),
 CONSTRAINT ck_binding_state CHECK(state IN (0,10,20,30)),
 CONSTRAINT ck_binding_created CHECK(state <> 10 OR order_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申请唯一建单绑定与关闭墓碑';

CREATE TABLE trade_seckill_outbox (
 id bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
 tenant_id bigint NOT NULL COMMENT '租户',
 event_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定重投事件号',
 request_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请号',
 aggregate_version bigint unsigned NOT NULL COMMENT '结果事件版本',
 event_type varchar(64) NOT NULL COMMENT 'ORDER_CREATED/REQUEST_CLOSED等带版本事件',
 payload json NOT NULL COMMENT '最小结果载荷，不记录支付秘密/地址明文',
 status tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0待发10租约中20已发30死信',
 attempts int unsigned NOT NULL DEFAULT 0 COMMENT '重试次数',
 next_retry_time datetime(3) NOT NULL COMMENT '下次发送UTC',
 claim_token varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'worker租约令牌',
 locked_until datetime(3) DEFAULT NULL COMMENT '租约截止UTC',
 last_error varchar(500) NOT NULL DEFAULT '' COMMENT '脱敏错误',
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建UTC',
 update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_tout_event(tenant_id,event_id),
 UNIQUE KEY uk_tout_aggregate(tenant_id,request_no,aggregate_version,event_type),
 KEY idx_tout_delivery(status,next_retry_time,locked_until,id),
 CONSTRAINT ck_tout_status CHECK(status IN (0,10,20,30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='trade事务结果可靠投递';

CREATE TABLE trade_seckill_inbox (
 id bigint NOT NULL AUTO_INCREMENT COMMENT '消费凭证ID',
 tenant_id bigint NOT NULL COMMENT '租户',
 consumer_group varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '消费组',
 event_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原事件号',
 payload_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同键异参检查',
 request_no varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请号',
 result_code varchar(64) NOT NULL COMMENT '建单或关闭最终消费结果',
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事务消费完成UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_tin_event(tenant_id,consumer_group,event_id),
 KEY idx_tin_request(tenant_id,request_no,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='trade消费幂等凭证';
