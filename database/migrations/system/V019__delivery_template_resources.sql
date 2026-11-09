-- DEC-046/065: merchant delivery templates only; no role or package auto-expansion.
-- New resource catalog + explicit direct-SQL audit, never impersonates an HTTP operation.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE delivery_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE delivery_resource_seed(
 id BIGINT NOT NULL PRIMARY KEY,name VARCHAR(50) NOT NULL,permission VARCHAR(100) NOT NULL,
 type TINYINT NOT NULL,sort INT NOT NULL,parent_id BIGINT NOT NULL,path VARCHAR(200) NOT NULL,component VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
INSERT INTO delivery_resource_seed VALUES
 (230000,'配送管理','',1,30,0,'',''),
 (230100,'运费模板','trade:delivery:express-template:query',2,1,230000,'/admin/shipping/templates','DeliveryTemplates'),
 (230101,'新增运费模板','trade:delivery:express-template:create',3,1,230100,'',''),
 (230102,'修改运费模板','trade:delivery:express-template:update',3,2,230100,'',''),
 (230103,'删除运费模板','trade:delivery:express-template:delete',3,3,230100,'','');
START TRANSACTION;
INSERT INTO delivery_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @delivery_seed_actor,@delivery_seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO delivery_resource_guard
SELECT IF(@delivery_seed_actor>0 AND @delivery_seed_role>0
 AND EXISTS(SELECT 1 FROM system_users WHERE id=@delivery_seed_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@delivery_seed_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@delivery_seed_actor AND role_id=@delivery_seed_role AND tenant_id=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.role_id=@delivery_seed_role AND rm.tenant_id=0 AND rm.deleted=b'0'
   AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN delivery_resource_seed s
  ON m.id=s.id OR (m.realm='merchant' AND s.permission<>'' AND m.permission=s.permission)
   OR (m.realm='merchant' AND s.path<>'' AND m.path=s.path)),1,0);
SET @delivery_seed_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,'merchant',name,permission,type,sort,parent_id,path,'',component,'',
 0,IF(type=3,b'0',b'1'),b'0',b'0',0,'sql-delivery-resource-seed','sql-delivery-resource-seed',b'0'
FROM delivery_resource_seed;
SET @delivery_seed_count=ROW_COUNT();
INSERT INTO delivery_resource_guard SELECT IF(@delivery_seed_count=5,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
SELECT 0,0,@delivery_seed_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V019-delivery-templates','runId',@delivery_seed_run,
  'realm','merchant','permission',permission),'' FROM delivery_resource_seed;
INSERT INTO delivery_resource_guard SELECT IF(ROW_COUNT()=5,1,0);
COMMIT;
SELECT 'DELIVERY_RESOURCES_SEEDED' AS result,@delivery_seed_count AS menus,@delivery_seed_run AS run_id;
