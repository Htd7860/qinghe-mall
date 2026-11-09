-- Author: assistant, DEC-017/020/022/046/057/058.
-- Target: local qinghe_system, MySQL 8.0.16+; preflight all four objects before executing.
-- CREATE only. No grants, role/menu/user seeds, foreign keys or destructive commands.
-- DDL implicitly commits; stop on failure and inspect partial state, never blindly rerun.

CREATE TABLE `system_role` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色编号',
    `tenant_id` bigint NOT NULL COMMENT '可信租户，平台0、商家正数，不设默认值',
    `name` varchar(30) NOT NULL COMMENT '角色显示名称',
    `code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同租户角色码，删除后保留，不仅凭角色码授予特权',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `status` tinyint NOT NULL COMMENT '0启用、1停用；停用保留绑定，鉴权排除',
    `type` tinyint NOT NULL COMMENT '1内置、2自定义，由可信服务端决定',
    `remark` varchar(500) DEFAULT NULL COMMENT '角色说明',
    `data_scope` tinyint NOT NULL COMMENT '1本租户全部、2指定部门、3本部门、4部门及下级、5本人，后续真实数据权限执行',
    `data_scope_dept_ids` json NOT NULL COMMENT '自定义部门ID数组，非自定义范围时空数组；元素及同租户归属由服务校验',
    `auth_version` bigint NOT NULL DEFAULT 0 COMMENT '角色授权变更版本，后续条件更新防旧命令覆盖',
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除，恢复不自动恢复已撤销关联',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_tenant_code` (`tenant_id`, `code`),
    KEY `idx_role_tenant_status` (`tenant_id`, `deleted`, `status`, `sort`, `id`),
    CONSTRAINT `chk_role_tenant` CHECK (`tenant_id` >= 0),
    CONSTRAINT `chk_role_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `chk_role_code` CHECK (CHAR_LENGTH(TRIM(`code`)) > 0 AND CHAR_LENGTH(`code`) = CHAR_LENGTH(TRIM(`code`))),
    CONSTRAINT `chk_role_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_role_type` CHECK (`type` IN (1, 2)),
    CONSTRAINT `chk_role_data_scope` CHECK (`data_scope` IN (1, 2, 3, 4, 5)),
    CONSTRAINT `chk_role_dept_ids` CHECK (JSON_TYPE(`data_scope_dept_ids`) = 'ARRAY'),
    CONSTRAINT `chk_role_auth_version` CHECK (`auth_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台及商家租户内RBAC角色';

CREATE TABLE `system_menu` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '全局资源编号',
    `realm` varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'merchant商家或platform平台资源，服务层校验父子和授权端一致',
    `name` varchar(50) NOT NULL,
    `permission` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT '权限标识；目录可空，同一权限可由多个资源表达，不设唯一键',
    `type` tinyint NOT NULL COMMENT '1目录、2菜单、3按钮或接口权限',
    `sort` int NOT NULL DEFAULT 0,
    `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '根为0，同端父资源存在及防环由服务校验',
    `path` varchar(200) NOT NULL DEFAULT '',
    `icon` varchar(100) NOT NULL DEFAULT '',
    `component` varchar(255) NOT NULL DEFAULT '',
    `component_name` varchar(255) NOT NULL DEFAULT '',
    `status` tinyint NOT NULL COMMENT '0启用、1停用，停用排除实际权限',
    `visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '导航是否可见，不等于鉴权开关',
    `keep_alive` bit(1) NOT NULL DEFAULT b'0',
    `always_show` bit(1) NOT NULL DEFAULT b'0',
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (`id`),
    KEY `idx_menu_realm_parent` (`realm`, `parent_id`, `deleted`, `sort`, `id`),
    KEY `idx_menu_realm_permission` (`realm`, `permission`, `deleted`, `status`),
    CONSTRAINT `chk_menu_realm` CHECK (`realm` IN ('merchant', 'platform')),
    CONSTRAINT `chk_menu_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `chk_menu_type` CHECK (`type` IN (1, 2, 3)),
    CONSTRAINT `chk_menu_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_menu_parent` CHECK (`parent_id` >= 0),
    CONSTRAINT `chk_menu_button_permission` CHECK (`type` <> 3 OR CHAR_LENGTH(TRIM(`permission`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台维护的分端全局菜单和权限资源目录';

CREATE TABLE `system_user_role` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL COMMENT '可信租户；用户和角色必须同属此租户，服务层校验',
    `user_id` bigint NOT NULL,
    `role_id` bigint NOT NULL,
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '撤销逻辑删除，显式重授恢复原行',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role_binding` (`tenant_id`, `user_id`, `role_id`),
    KEY `idx_user_role_reverse` (`tenant_id`, `role_id`, `deleted`, `user_id`),
    CONSTRAINT `chk_user_role_tenant` CHECK (`tenant_id` >= 0),
    CONSTRAINT `chk_user_role_user` CHECK (`user_id` > 0),
    CONSTRAINT `chk_user_role_role` CHECK (`role_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户内用户角色关联，显式重授恢复原记录';

CREATE TABLE `system_role_menu` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tenant_id` bigint NOT NULL COMMENT '可信角色归属租户，平台0、商家正数',
    `role_id` bigint NOT NULL,
    `menu_id` bigint NOT NULL COMMENT '全局资源，所属端及套餐范围由授权服务校验',
    `creator` varchar(64) NOT NULL DEFAULT '',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updater` varchar(64) NOT NULL DEFAULT '',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '撤销逻辑删除，显式重授恢复原行',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu_binding` (`tenant_id`, `role_id`, `menu_id`),
    KEY `idx_role_menu_reverse` (`tenant_id`, `menu_id`, `deleted`, `role_id`),
    CONSTRAINT `chk_role_menu_tenant` CHECK (`tenant_id` >= 0),
    CONSTRAINT `chk_role_menu_role` CHECK (`role_id` > 0),
    CONSTRAINT `chk_role_menu_menu` CHECK (`menu_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户角色资源关联，仍需核对端和套餐上限';
