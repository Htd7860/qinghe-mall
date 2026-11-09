-- Author: assistant, DEC-017/046/048. Model: TenantPackageDO, 11 columns including BaseDo.
-- Select qinghe_system and verify system_tenant_package is absent before execution.
-- MySQL 8.0.16+; single CREATE only, no packages/accounts/menus or foreign keys.
-- DDL implicitly commits. Do not rerun against an existing table.

CREATE TABLE `system_tenant_package` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户套餐编号，全局自增',
    `name` varchar(30) NOT NULL COMMENT '套餐名称，全局唯一，逻辑删除不释放名称',
    `status` tinyint NOT NULL COMMENT '0开启，1停用；停用仅禁止新开通或切入，已有租户不因此统一失效',
    `remark` varchar(256) DEFAULT NULL COMMENT '套餐说明',
    `menu_ids` json NOT NULL COMMENT '允许的菜单资源编号数组，空数组表示无授权，资源存在及合法性由服务层校验',
    `auth_version` bigint NOT NULL DEFAULT 0 COMMENT '套餐资源授权版本，后续由变更流程维护并校验提交版本',
    `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者标识，由服务端维护',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者标识，由服务端维护',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除；在用套餐不可删，由服务层事务检查',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_package_name` (`name`),
    KEY `idx_tenant_package_status` (`status`, `deleted`, `id`),
    CONSTRAINT `chk_tenant_package_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `chk_tenant_package_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_tenant_package_menu_ids` CHECK (JSON_TYPE(`menu_ids`) = 'ARRAY'),
    CONSTRAINT `chk_tenant_package_auth_version` CHECK (`auth_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台维护的全局租户套餐及授权资源上限';
