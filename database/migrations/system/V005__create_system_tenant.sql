-- Author: assistant, DEC-017. Field plan: docs/design/TENANT-DATA-STRUCTURE.md.
-- NOT EXECUTED. Select qinghe_system and verify system_tenant is absent before running.
-- MySQL 8.0.16+ for enforced CHECK; current local server read-only verified as 8.0.43.
-- CREATE only: no users, tenant rows, packages, platform placeholder or foreign keys.
-- DDL implicitly commits. Do not rerun against an existing table.

CREATE TABLE `system_tenant` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商家租户编号，自增；平台身份上下文0不作为普通商家记录初始化',
    `code` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局商家编码，平台分配，3至32位小写字母数字短横线，创建后不改且删除不释放',
    `name` varchar(128) NOT NULL COMMENT '品牌显示名称，可修改、可重名',
    `contact_user_id` bigint DEFAULT NULL COMMENT '租户管理员用户逻辑关联，创建管理员后在事务内补齐，不建外键',
    `contact_name` varchar(64) NOT NULL DEFAULT '' COMMENT '联系人，不用于登录定位',
    `contact_mobile` varchar(32) NOT NULL DEFAULT '' COMMENT '联系号码，不作为跨租户账号唯一身份',
    `status` tinyint NOT NULL COMMENT '运营状态：0开启，1关闭；受限履约售后按业务授权判断',
    `package_id` bigint NOT NULL COMMENT '真实租户套餐编号，由服务层验证存在及有效性',
    `expire_time` datetime(6) NOT NULL COMMENT '套餐到期时间；过期记录保留，按业务操作实时判断权限',
    `account_count` int NOT NULL COMMENT '未删除后台账号数上限，含管理员与禁用账号',
    `auth_version` bigint NOT NULL DEFAULT 0 COMMENT '授权状态版本，后续由服务层随授权变化维护并在提交点检查',
    `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者标识，由服务端维护',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者标识，由服务端维护',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除，保留商家编码，不级联删除业务记录',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`code`),
    KEY `idx_tenant_package` (`package_id`, `id`),
    KEY `idx_tenant_expire` (`expire_time`, `id`),
    CONSTRAINT `chk_tenant_code` CHECK (
        CHAR_LENGTH(`code`) BETWEEN 3 AND 32
        AND NOT REGEXP_LIKE(`code`, '[^a-z0-9-]', 'c')
    ),
    CONSTRAINT `chk_tenant_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `chk_tenant_contact_user` CHECK (`contact_user_id` IS NULL OR `contact_user_id` > 0),
    CONSTRAINT `chk_tenant_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_tenant_package` CHECK (`package_id` > 0),
    CONSTRAINT `chk_tenant_account_count` CHECK (`account_count` >= 1),
    CONSTRAINT `chk_tenant_auth_version` CHECK (`auth_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家租户全局注册信息';

