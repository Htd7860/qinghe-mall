-- System 后台员工表：设计评审稿，未执行。
-- 依据：本地 yudao AdminUserDO、BaseDO、TenantBaseDO 与 system_users DDL。
-- 已确认业务规则：DEC-019；助手编写 SQL 的授权：DEC-017。
-- 在指定的隔离空 schema 中验证；脚本不创建数据库、不选择库、不删除已有表。
-- 保留 yudao 字段及逻辑删除语义；唯一性、索引与少数默认值差异见同目录 README。

CREATE TABLE `system_users` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '后台用户编号',
    `username` varchar(30) NOT NULL COMMENT '登录账号，同租户内唯一，逻辑删除后不释放',
    `password` varchar(100) NOT NULL COMMENT '密码编码结果，不存明文，不返回页面',
    `nickname` varchar(30) NOT NULL COMMENT '用户昵称',
    `remark` varchar(500) DEFAULT NULL COMMENT '管理备注',
    `dept_id` bigint DEFAULT NULL COMMENT '部门编号，逻辑关联，需校验租户归属',
    `post_ids` varchar(255) DEFAULT NULL COMMENT '岗位编号集合，与上游 JSON TypeHandler 对齐',
    `email` varchar(50) DEFAULT '' COMMENT '邮箱',
    `mobile` varchar(11) DEFAULT '' COMMENT '手机号，沿用当前参考字段长度',
    `sex` tinyint DEFAULT 0 COMMENT '性别，取值与对应枚举一致',
    `avatar` varchar(512) DEFAULT '' COMMENT '头像地址',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '账号状态：0开启，1关闭',
    `login_ip` varchar(50) DEFAULT '' COMMENT '最后登录IP',
    `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者标识，由服务端维护',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者标识，由服务端维护',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除：0未删除，1已删除',
    `tenant_id` bigint NOT NULL COMMENT '所属租户，由可信上下文提供，不默认填0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_tenant_username` (`tenant_id`, `username`),
    KEY `idx_users_tenant_status` (`tenant_id`, `deleted`, `status`, `id`),
    KEY `idx_users_tenant_dept` (`tenant_id`, `dept_id`, `deleted`),
    KEY `idx_users_tenant_mobile` (`tenant_id`, `mobile`, `deleted`),
    KEY `idx_users_tenant_email` (`tenant_id`, `email`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '后台员工信息，与消费者会员分开建模';
