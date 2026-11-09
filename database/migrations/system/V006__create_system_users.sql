-- Author: assistant, DEC-017/046. Model: user-written AdminUserDO (21 fields including inherited).
-- Select qinghe_system and verify system_users is absent before execution.
-- Replaces the unexecuted root V001 draft for the current hand-written backend; never run both.
-- MySQL 8.0.16+; current target verified as 8.0.43, hostname QIANG.
-- Single CREATE only; no accounts/passwords, tenant rows, privileges, foreign keys or other writes.
-- DDL implicitly commits. Do not rerun against an existing table.

CREATE TABLE `system_users` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '后台账号编号，全表自增唯一',
    `username` varchar(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登录账号，4至30位字母数字，区分大小写，同租户唯一，删除不释放',
    `password` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '密码编码结果，支持编码器标识，不存明文，不向页面或日志输出',
    `nickname` varchar(30) NOT NULL COMMENT '用户显示名称',
    `remark` varchar(500) DEFAULT NULL COMMENT '管理备注',
    `dept_id` bigint DEFAULT NULL COMMENT '部门编号，由服务层验证本租户归属',
    `post_ids` json DEFAULT NULL COMMENT '岗位编号JSON数组，由服务层验证元素、去重及本租户归属；不是RBAC角色集合',
    `email` varchar(50) NOT NULL DEFAULT '' COMMENT '联系邮箱，不作为全局唯一身份',
    `mobile` varchar(11) NOT NULL DEFAULT '' COMMENT '国内联系手机号，不作为跨租户唯一身份',
    `sex` tinyint NOT NULL DEFAULT 0 COMMENT '性别资料：0未知，1男，2女',
    `avatar` varchar(512) NOT NULL DEFAULT '' COMMENT '头像引用或地址，由应用校验',
    `status` tinyint NOT NULL COMMENT '账号状态：0开启，1关闭；创建时由服务端明确设置',
    `login_ip` varchar(50) NOT NULL DEFAULT '' COMMENT '最后成功登录IP，由服务端维护',
    `login_date` datetime(6) DEFAULT NULL COMMENT '最后成功登录时间，尚未登录可空',
    `auth_version` bigint NOT NULL DEFAULT 0 COMMENT '账号授权状态版本，后续由Service维护并建立提交点校验协议',
    `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者标识，由服务端维护',
    `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者标识，由服务端维护',
    `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除，保留登录标识，恢复原账号需重新校验额度和权限',
    `tenant_id` bigint NOT NULL COMMENT '可信归属；平台后台明确为0，商家员工为正数，不设默认值',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_tenant_username` (`tenant_id`, `username`),
    KEY `idx_users_tenant_status` (`tenant_id`, `deleted`, `status`, `id`),
    KEY `idx_users_tenant_dept` (`tenant_id`, `dept_id`, `deleted`),
    KEY `idx_users_tenant_mobile` (`tenant_id`, `mobile`, `deleted`),
    KEY `idx_users_tenant_email` (`tenant_id`, `email`, `deleted`),
    CONSTRAINT `chk_users_username` CHECK (
        CHAR_LENGTH(`username`) BETWEEN 4 AND 30
        AND NOT REGEXP_LIKE(`username`, '[^A-Za-z0-9]', 'c')
    ),
    CONSTRAINT `chk_users_password` CHECK (CHAR_LENGTH(TRIM(`password`)) > 0),
    CONSTRAINT `chk_users_nickname` CHECK (CHAR_LENGTH(TRIM(`nickname`)) > 0),
    CONSTRAINT `chk_users_dept` CHECK (`dept_id` IS NULL OR `dept_id` > 0),
    CONSTRAINT `chk_users_post_ids` CHECK (`post_ids` IS NULL OR JSON_TYPE(`post_ids`) = 'ARRAY'),
    CONSTRAINT `chk_users_sex` CHECK (`sex` IN (0, 1, 2)),
    CONSTRAINT `chk_users_status` CHECK (`status` IN (0, 1)),
    CONSTRAINT `chk_users_auth_version` CHECK (`auth_version` >= 0),
    CONSTRAINT `chk_users_tenant` CHECK (`tenant_id` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台后台人员与商家员工账号，消费者会员独立建模';
