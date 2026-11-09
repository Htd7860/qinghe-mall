-- DEC-046/065: merchant product SPU and manual inventory only; no role or package auto-expansion.
-- New resource catalog + explicit direct-SQL audit, never impersonates an HTTP operation.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE spu_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE spu_resource_seed(
 id BIGINT NOT NULL PRIMARY KEY,name VARCHAR(50) NOT NULL,permission VARCHAR(100) NOT NULL,
 type TINYINT NOT NULL,sort INT NOT NULL,parent_id BIGINT NOT NULL,path VARCHAR(200) NOT NULL,component VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
INSERT INTO spu_resource_seed VALUES
 (210400,'商品管理','product:spu:query',2,4,210000,'/admin/products','ProductManagement'),
 (210401,'新增商品','product:spu:create',3,1,210400,'',''),
 (210402,'修改商品与上下架','product:spu:update',3,2,210400,'',''),
 (210403,'删除回收站商品','product:spu:delete',3,3,210400,'',''),
 (210404,'库存调整','product:spu:update-stock',3,4,210400,'','');
START TRANSACTION;
INSERT INTO spu_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @spu_seed_actor,@spu_seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO spu_resource_guard
SELECT IF(@spu_seed_actor>0 AND @spu_seed_role>0
 AND EXISTS(SELECT 1 FROM system_users WHERE id=@spu_seed_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@spu_seed_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@spu_seed_actor AND role_id=@spu_seed_role AND tenant_id=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.role_id=@spu_seed_role AND rm.tenant_id=0 AND rm.deleted=b'0'
   AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=210000 AND realm='merchant' AND parent_id=0 AND type=1 AND status=0 AND deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN spu_resource_seed s
  ON m.id=s.id OR (m.realm='merchant' AND s.permission<>'' AND m.permission=s.permission)
   OR (m.realm='merchant' AND s.path<>'' AND m.path=s.path)),1,0);
SET @spu_seed_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,'merchant',name,permission,type,sort,parent_id,path,'',component,'',
 0,IF(type=3,b'0',b'1'),b'0',b'0',0,'sql-product-spu-seed','sql-product-spu-seed',b'0'
FROM spu_resource_seed;
SET @spu_seed_count=ROW_COUNT();
INSERT INTO spu_resource_guard SELECT IF(@spu_seed_count=5,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
SELECT 0,0,@spu_seed_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V020-product-spu','runId',@spu_seed_run,
  'realm','merchant','permission',permission),'' FROM spu_resource_seed;
INSERT INTO spu_resource_guard SELECT IF(ROW_COUNT()=5,1,0);
COMMIT;
SELECT 'PRODUCT_SPU_RESOURCES_SEEDED' AS result,@spu_seed_count AS menus,@spu_seed_run AS run_id;
