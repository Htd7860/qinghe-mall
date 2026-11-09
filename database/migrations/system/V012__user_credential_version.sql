-- DEC-019/062: persisted session generation; re-enabling an account must not revive old credentials.
-- No account, password or Token values are read or changed by this migration.
ALTER TABLE `system_users`
    ADD COLUMN `credential_version` bigint NOT NULL DEFAULT 0 COMMENT '密码重置/停用/删除后递增，旧会话不可恢复' AFTER `auth_version`,
    ADD CONSTRAINT `chk_users_credential_version` CHECK (`credential_version` >= 0);

ALTER TABLE `system_oauth2_refresh_token`
    ADD COLUMN `credential_version` bigint NOT NULL DEFAULT 0 COMMENT '签发时账号凭据版本，刷新必须仍匹配' AFTER `user_type`,
    ADD CONSTRAINT `chk_refresh_credential_version` CHECK (`credential_version` >= 0);
