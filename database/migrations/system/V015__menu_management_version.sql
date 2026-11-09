-- DEC-062. Configuration version for global menu lifecycle writes.
ALTER TABLE `system_menu`
    ADD COLUMN `auth_version` bigint NOT NULL DEFAULT 0 AFTER `always_show`,
    ADD CONSTRAINT `chk_menu_auth_version` CHECK (`auth_version` >= 0);
