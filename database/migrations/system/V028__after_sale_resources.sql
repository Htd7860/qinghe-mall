-- DEC-046/065/066. Hidden merchant aftersale query/audit/inspection resource metadata only.
-- Existing trade parent, packages, user/role bindings and visibility stay unchanged.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE after_sale_resource_seed(
 id BIGINT NOT NULL PRIMARY KEY,name VARCHAR(50) NOT NULL,permission VARCHAR(100) NOT NULL,
 type TINYINT NOT NULL,sort INT NOT NULL,parent_id BIGINT NOT NULL,path VARCHAR(200) NOT NULL,component VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
INSERT INTO after_sale_resource_seed VALUES
 (250300,'售后查询','trade:after-sale:query',2,3,250000,'/admin/after-sales','AfterSales'),
 (250301,'售后审核及寄回地址','trade:after-sale:audit',3,1,250300,'',''),
 (250302,'退货验货','trade:after-sale:receive',3,2,250300,'','');
START TRANSACTION;
INSERT INTO after_sale_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @after_sale_resource_seed_actor,@after_sale_resource_seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO after_sale_resource_guard
SELECT IF(@after_sale_resource_seed_actor>0 AND @after_sale_resource_seed_role>0
 AND EXISTS(SELECT 1 FROM system_users WHERE id=@after_sale_resource_seed_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@after_sale_resource_seed_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@after_sale_resource_seed_actor AND role_id=@after_sale_resource_seed_role AND tenant_id=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.role_id=@after_sale_resource_seed_role AND rm.tenant_id=0 AND rm.deleted=b'0'
   AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=250000 AND realm='merchant' AND type=1 AND parent_id=0 AND status=0 AND deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN after_sale_resource_seed s
  ON m.id=s.id OR (m.realm='merchant' AND m.permission=s.permission)
   OR (m.realm='merchant' AND s.path<>'' AND m.path=s.path)),1,0);
SET @after_sale_resource_seed_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,'merchant',name,permission,type,sort,parent_id,path,'',component,'',
 0,b'0',b'0',b'0',0,'sql-after-sale-resource-resource-seed','sql-after-sale-resource-resource-seed',b'0'
FROM after_sale_resource_seed;
SET @after_sale_resource_seed_count=ROW_COUNT();
INSERT INTO after_sale_resource_guard SELECT IF(@after_sale_resource_seed_count=3,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
SELECT 0,0,@after_sale_resource_seed_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V028-after-sale-resource','runId',@after_sale_resource_seed_run,
  'realm','merchant','permission',permission,'hidden',TRUE),'' FROM after_sale_resource_seed;
INSERT INTO after_sale_resource_guard SELECT IF(ROW_COUNT()=3,1,0);
COMMIT;
SELECT 'AFTER_SALE_RESOURCES_SEEDED' AS result,@after_sale_resource_seed_count AS menus,@after_sale_resource_seed_run AS run_id;
