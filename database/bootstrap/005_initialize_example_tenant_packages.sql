-- 用户授权的本机示例套餐初始化；目标仅 QIANG / MySQL8 / qinghe_system。
-- 不创建租户或员工，不覆盖同名套餐；已有配置不一致时中止并回滚。
-- 使用 mysql --skip-reconnect，不加 --force；同名且相同的初始化记录允许跳过。
-- actor引用现有平台初始化管理员，审计明确标记直接SQL初始化，非HTTP登录操作。
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout = 10;

CREATE TEMPORARY TABLE package_seed_guard (
    item VARCHAR(64) NOT NULL,
    valid TINYINT NOT NULL,
    CONSTRAINT chk_package_seed_guard CHECK (valid = 1)
) ENGINE=InnoDB;

CREATE TEMPORARY TABLE package_seed_catalog (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL,
    type INT NOT NULL,
    permission VARCHAR(128) NOT NULL,
    auth_version BIGINT NOT NULL
) ENGINE=InnoDB;

INSERT INTO package_seed_catalog VALUES
    (200000,0,1,'',5),
    (200100,200000,2,'system:user:query',3),
    (200101,200100,3,'system:user:create',0),
    (200102,200100,3,'system:user:update',0),
    (200103,200100,3,'system:user:delete',0),
    (200104,200100,3,'system:user:restore',0),
    (200105,200100,3,'system:user:update-password',0),
    (200120,200100,3,'system:permission:assign-user-role',0),
    (200200,200000,2,'system:role:query',0),
    (200201,200200,3,'system:role:create',0),
    (200202,200200,3,'system:role:update',0),
    (200203,200200,3,'system:role:delete',0),
    (200204,200200,3,'system:role:restore',0),
    (200220,200200,3,'system:permission:assign-role-menu',0),
    (200221,200200,3,'system:permission:assign-role-data-scope',0),
    (200300,200000,2,'system:dept:query',0),
    (200301,200300,3,'system:dept:create',0),
    (200302,200300,3,'system:dept:update',0),
    (200303,200300,3,'system:dept:delete',0),
    (200304,200300,3,'system:dept:restore',0);

CREATE TEMPORARY TABLE package_seed_requests (
    name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci PRIMARY KEY,
    remark VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    menu_ids JSON NOT NULL,
    existing_id BIGINT NULL
) ENGINE=InnoDB;

INSERT INTO package_seed_requests (name,remark,menu_ids) VALUES
    ('只读体验版','示例套餐：查看员工、角色、部门，不开放管理写操作。',
     JSON_ARRAY(200000,200100,200200,200300)),
    ('人员管理版','示例套餐：员工及部门的查看、新增、修改、启停、删除、恢复，含员工重置密码；不开放角色授权管理。',
     JSON_ARRAY(200000,200100,200101,200102,200103,200104,200105,
                200300,200301,200302,200303,200304)),
    ('完整管理版','示例套餐：开放当前已实现的全部商家后台资源，包含员工、部门、角色、资源授权及数据范围分配。',
     JSON_ARRAY(200000,200100,200101,200102,200103,200104,200105,200120,
                200200,200201,200202,200203,200204,200220,200221,
                200300,200301,200302,200303,200304));

START TRANSACTION;

INSERT INTO package_seed_guard VALUES
    ('local_target', IF(DATABASE()='qinghe_system' AND @@hostname='QIANG'
        AND VERSION() LIKE '8.%',1,0));
INSERT INTO package_seed_guard
    SELECT 'transactional_tables', IF(COUNT(*)=3,1,0)
    FROM information_schema.tables
    WHERE table_schema=DATABASE() AND engine='InnoDB'
      AND table_name IN ('system_tenant_package','system_management_audit','system_management_mutex');

-- 与现有后台管理写入共用同一行锁。
SET @seed_actor = NULL;
SET @seed_role = NULL;
SELECT platform_admin_user_id,platform_admin_role_id INTO @seed_actor,@seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;

INSERT INTO package_seed_guard
    SELECT 'platform_initializer', IF(COUNT(*)=1,1,0)
    FROM system_users u
    JOIN system_role r ON r.id=@seed_role AND r.tenant_id=0
        AND r.type=1 AND r.status=0 AND r.deleted=b'0'
    JOIN system_user_role ur ON ur.user_id=u.id AND ur.role_id=r.id
        AND ur.tenant_id=0 AND ur.deleted=b'0'
    JOIN system_role_menu rm ON rm.role_id=r.id AND rm.menu_id=100201
        AND rm.tenant_id=0 AND rm.deleted=b'0'
    JOIN system_menu m ON m.id=rm.menu_id AND m.realm='platform'
        AND m.permission='system:tenant-package:create' AND m.status=0 AND m.deleted=b'0'
    WHERE u.id=@seed_actor AND u.tenant_id=0 AND u.status=0 AND u.deleted=b'0';

-- 固定本次预检资源快照；用户后续改了资源结构/版本，应重新评审而非盲目重跑。
SELECT m.id FROM system_menu m JOIN package_seed_catalog c ON c.id=m.id FOR SHARE;
INSERT INTO package_seed_guard
    SELECT 'merchant_resource_snapshot', IF(COUNT(*)=20,1,0)
    FROM system_menu m JOIN package_seed_catalog c ON c.id=m.id
    WHERE m.realm='merchant' AND m.status=0 AND m.deleted=b'0'
      AND m.parent_id=c.parent_id AND m.type=c.type
      AND m.permission=c.permission AND m.auth_version=c.auth_version;

INSERT INTO package_seed_guard
    SELECT 'complete_parent_paths', IF(COUNT(*)=0,1,0)
    FROM package_seed_requests s
    JOIN JSON_TABLE(s.menu_ids,'$[*]' COLUMNS(menu_id BIGINT PATH '$')) selected
    LEFT JOIN system_menu m ON m.id=selected.menu_id
    WHERE m.id IS NULL OR m.realm<>'merchant' OR m.status<>0 OR m.deleted<>b'0'
       OR (m.parent_id<>0 AND JSON_CONTAINS(s.menu_ids,JSON_ARRAY(m.parent_id))=0);

UPDATE package_seed_requests s
LEFT JOIN system_tenant_package p ON p.name=s.name
SET s.existing_id=p.id;

INSERT INTO package_seed_guard
    SELECT 'existing_names_unchanged', IF(COUNT(*)=0,1,0)
    FROM package_seed_requests s JOIN system_tenant_package p ON p.id=s.existing_id
    WHERE p.deleted<>b'0' OR p.status<>0 OR p.auth_version<>0
       OR p.creator<>'sql-package-bootstrap' OR NOT (p.remark <=> s.remark)
       OR JSON_LENGTH(p.menu_ids)<>JSON_LENGTH(s.menu_ids)
       OR JSON_CONTAINS(p.menu_ids,s.menu_ids)=0 OR JSON_CONTAINS(s.menu_ids,p.menu_ids)=0;

SET @seed_time = CURRENT_TIMESTAMP(6);
SET @seed_run = UUID();
INSERT INTO system_tenant_package
    (name,status,remark,menu_ids,auth_version,creator,create_time,updater,update_time,deleted)
SELECT name,0,remark,menu_ids,0,'sql-package-bootstrap',@seed_time,
       'sql-package-bootstrap',@seed_time,b'0'
FROM package_seed_requests WHERE existing_id IS NULL ORDER BY JSON_LENGTH(menu_ids);
SET @seed_inserted = ROW_COUNT();

INSERT INTO system_management_audit
    (tenant_id,actor_tenant_id,actor_user_id,action,target_id,
     from_version,to_version,details,trace_id,create_time)
SELECT 0,0,@seed_actor,'PACKAGE.SEED',p.id,-1,0,
       JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
                   'actorContext','designated-platform-initializer',
                   'batch','005-example-tenant-packages','runId',@seed_run,
                   'menuIds',s.menu_ids),
       '',@seed_time
FROM package_seed_requests s JOIN system_tenant_package p ON p.name=s.name
WHERE s.existing_id IS NULL;
SET @seed_audited = ROW_COUNT();
INSERT INTO package_seed_guard VALUES ('audit_matches_insert',IF(@seed_inserted=@seed_audited,1,0));

COMMIT;

SELECT @seed_inserted AS inserted_packages,@seed_audited AS inserted_audits,@seed_run AS run_id;
SELECT p.id,p.name,p.status,p.auth_version,JSON_LENGTH(p.menu_ids) AS resource_count,p.menu_ids
FROM system_tenant_package p JOIN package_seed_requests s ON s.name=p.name
ORDER BY JSON_LENGTH(p.menu_ids);
