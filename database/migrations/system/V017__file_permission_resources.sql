-- DEC-046/065: file HTTP resources and designated platform administrator bootstrap grant.
-- Run once after V016 and the successful platform-admin bootstrap on local QIANG/qinghe_system.
-- No existing menu/package configuration is overwritten; merchant packages are NOT auto-expanded.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout = 10;
CREATE TEMPORARY TABLE file_resource_seed_guard(ok TINYINT NOT NULL CHECK(ok = 1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE file_resource_seed(
    id BIGINT NOT NULL PRIMARY KEY, realm VARCHAR(16) NOT NULL, name VARCHAR(50) NOT NULL,
    permission VARCHAR(100) NOT NULL, type TINYINT NOT NULL, sort INT NOT NULL, parent_id BIGINT NOT NULL
) ENGINE=InnoDB;
INSERT INTO file_resource_seed VALUES
 (109100,'platform','文件素材','infra:file:query',2,90,100000),
 (109101,'platform','上传文件','infra:file:create',3,1,109100),
 (109102,'platform','删除文件','infra:file:delete',3,2,109100),
 (209100,'merchant','文件素材','infra:file:query',2,90,200000),
 (209101,'merchant','上传文件','infra:file:create',3,1,209100),
 (209102,'merchant','删除文件','infra:file:delete',3,2,209100);

START TRANSACTION;
INSERT INTO file_resource_seed_guard
SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);

SELECT platform_admin_user_id,platform_admin_role_id INTO @file_seed_actor,@file_seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO file_resource_seed_guard
SELECT IF(@file_seed_actor>0 AND @file_seed_role>0
 AND EXISTS(SELECT 1 FROM system_users WHERE id=@file_seed_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@file_seed_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@file_seed_actor AND role_id=@file_seed_role AND tenant_id=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
    WHERE rm.role_id=@file_seed_role AND rm.tenant_id=0 AND rm.deleted=b'0'
      AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0'),1,0);

INSERT INTO file_resource_seed_guard
SELECT IF((SELECT COUNT(*) FROM system_menu WHERE (id=100000 AND realm='platform' OR id=200000 AND realm='merchant')
 AND parent_id=0 AND type=1 AND status=0 AND deleted=b'0')=2
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN file_resource_seed s ON m.id=s.id OR (m.realm=s.realm AND m.permission=s.permission)),1,0);
SELECT auth_version INTO @file_seed_role_version FROM system_role
WHERE id=@file_seed_role AND tenant_id=0 FOR UPDATE;
SET @file_seed_run=UUID();

INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,realm,name,permission,type,sort,parent_id,'','','','',0,b'0',b'0',b'0',0,
 'sql-file-resource-seed','sql-file-resource-seed',b'0' FROM file_resource_seed;
SET @file_seed_menus=ROW_COUNT();

INSERT INTO system_role_menu(tenant_id,role_id,menu_id,creator,updater,deleted)
SELECT 0,@file_seed_role,id,'sql-file-resource-seed','sql-file-resource-seed',b'0'
FROM file_resource_seed WHERE realm='platform';
SET @file_seed_bindings=ROW_COUNT();

UPDATE system_role SET auth_version=auth_version+1,updater='sql-file-resource-seed'
WHERE id=@file_seed_role AND tenant_id=0 AND auth_version=@file_seed_role_version;
INSERT INTO file_resource_seed_guard SELECT IF(ROW_COUNT()=1 AND @file_seed_menus=6 AND @file_seed_bindings=3,1,0);

INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
SELECT 0,0,@file_seed_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V017-file-permissions','runId',@file_seed_run,
  'realm',realm,'permission',permission),'' FROM file_resource_seed;
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
VALUES(0,0,@file_seed_actor,'ROLE.SEED',@file_seed_role,@file_seed_role_version,@file_seed_role_version+1,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V017-file-permissions','runId',@file_seed_run,
  'addedMenuIds',JSON_ARRAY(109100,109101,109102)), '');
COMMIT;

SELECT 'FILE_RESOURCES_SEEDED' AS result,@file_seed_menus AS menu_rows,@file_seed_bindings AS platform_bindings,
 @file_seed_role AS role_id,@file_seed_role_version+1 AS role_version,@file_seed_run AS run_id;
