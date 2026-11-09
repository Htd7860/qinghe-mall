-- Author: assistant, DEC-017. First-party application plan: docs/design/OAUTH2-CLIENT-DATA.md.
-- NOT EXECUTED. Select qinghe_system and verify all three client_id values are absent first.
-- Development initial values: access 900 seconds, refresh 604800 seconds; not Gateway Cookie lifetime.
-- No users, passwords, tenant records, tokens, roles, secrets or wildcard scope grants.
-- One multi-row INSERT; duplicate client_id must fail, never silently overwrite or resurrect a client.

INSERT INTO `system_oauth2_client` (
    `client_id`, `name`, `status`, `realm`,
    `access_token_validity_seconds`, `refresh_token_validity_seconds`,
    `scopes`, `creator`, `updater`, `deleted`
) VALUES
    ('qinghe-platform', '平台后台', 0, 'platform',
     900, 604800, JSON_ARRAY(), 'bootstrap', 'bootstrap', b'0'),
    ('qinghe-admin', '商家后台', 0, 'merchant',
     900, 604800, JSON_ARRAY(), 'bootstrap', 'bootstrap', b'0'),
    ('qinghe-app', '消费者商城', 0, 'consumer',
     900, 604800, JSON_ARRAY(), 'bootstrap', 'bootstrap', b'0');

