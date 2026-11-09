-- DEC-046/065: merchant product catalog only; no role or package auto-expansion.
-- New resource catalog + explicit direct-SQL audit, never impersonates an HTTP operation.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE product_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE product_resource_seed(
 id BIGINT NOT NULL PRIMARY KEY,name VARCHAR(50) NOT NULL,permission VARCHAR(100) NOT NULL,
 type TINYINT NOT NULL,sort INT NOT NULL,parent_id BIGINT NOT NULL,path VARCHAR(200) NOT NULL,component VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
INSERT INTO product_resource_seed VALUES
 (210000,'商品资料','',1,20,0,'',''),
 (210100,'商品分类','product:category:query',2,1,210000,'/admin/catalog/categories','ProductCatalog'),
 (210101,'新增商品分类','product:category:create',3,1,210100,'',''),
 (210102,'修改商品分类','product:category:update',3,2,210100,'',''),
 (210103,'删除商品分类','product:category:delete',3,3,210100,'',''),
 (210200,'商品品牌','product:brand:query',2,2,210000,'/admin/catalog/brands','ProductCatalog'),
 (210201,'新增商品品牌','product:brand:create',3,1,210200,'',''),
 (210202,'修改商品品牌','product:brand:update',3,2,210200,'',''),
 (210203,'删除商品品牌','product:brand:delete',3,3,210200,'',''),
 (210300,'规格属性','product:property:query',2,3,210000,'/admin/catalog/properties','ProductCatalog'),
 (210301,'新增规格属性','product:property:create',3,1,210300,'',''),
 (210302,'修改规格属性','product:property:update',3,2,210300,'',''),
 (210303,'删除规格属性','product:property:delete',3,3,210300,'','');
START TRANSACTION;
INSERT INTO product_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @product_seed_actor,@product_seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO product_resource_guard
SELECT IF(@product_seed_actor>0 AND @product_seed_role>0
 AND EXISTS(SELECT 1 FROM system_users WHERE id=@product_seed_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@product_seed_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@product_seed_actor AND role_id=@product_seed_role AND tenant_id=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.role_id=@product_seed_role AND rm.tenant_id=0 AND rm.deleted=b'0'
   AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN product_resource_seed s
  ON m.id=s.id OR (m.realm='merchant' AND s.permission<>'' AND m.permission=s.permission)
   OR (m.realm='merchant' AND s.path<>'' AND m.path=s.path)),1,0);
SET @product_seed_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,'merchant',name,permission,type,sort,parent_id,path,'',component,'',
 0,IF(type=3,b'0',b'1'),b'0',b'0',0,'sql-product-resource-seed','sql-product-resource-seed',b'0'
FROM product_resource_seed;
SET @product_seed_count=ROW_COUNT();
INSERT INTO product_resource_guard SELECT IF(@product_seed_count=13,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
SELECT 0,0,@product_seed_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V018-product-catalog','runId',@product_seed_run,
  'realm','merchant','permission',permission),'' FROM product_resource_seed;
INSERT INTO product_resource_guard SELECT IF(ROW_COUNT()=13,1,0);
COMMIT;
SELECT 'PRODUCT_RESOURCES_SEEDED' AS result,@product_seed_count AS menus,@product_seed_run AS run_id;
