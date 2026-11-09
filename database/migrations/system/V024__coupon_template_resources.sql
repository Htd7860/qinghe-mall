-- DEC-046/065: merchant coupon templates only; no role or package auto-expansion.
-- New resource catalog + explicit direct-SQL audit, never impersonates an HTTP operation.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE coupon_template_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE coupon_template_resource_seed(
 id BIGINT NOT NULL PRIMARY KEY,name VARCHAR(50) NOT NULL,permission VARCHAR(100) NOT NULL,
 type TINYINT NOT NULL,sort INT NOT NULL,parent_id BIGINT NOT NULL,path VARCHAR(200) NOT NULL,component VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
INSERT INTO coupon_template_resource_seed VALUES
 (240000,'营销管理','',1,40,0,'',''),
 (240100,'优惠券模板','promotion:coupon-template:query',2,1,240000,'/admin/coupons','CouponTemplates'),
 (240101,'新增优惠券模板','promotion:coupon-template:create',3,1,240100,'',''),
 (240102,'修改及启停优惠券模板','promotion:coupon-template:update',3,2,240100,'',''),
 (240103,'删除优惠券模板','promotion:coupon-template:delete',3,3,240100,'','');
START TRANSACTION;
INSERT INTO coupon_template_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @coupon_template_seed_actor,@coupon_template_seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO coupon_template_resource_guard
SELECT IF(@coupon_template_seed_actor>0 AND @coupon_template_seed_role>0
 AND EXISTS(SELECT 1 FROM system_users WHERE id=@coupon_template_seed_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@coupon_template_seed_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@coupon_template_seed_actor AND role_id=@coupon_template_seed_role AND tenant_id=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.role_id=@coupon_template_seed_role AND rm.tenant_id=0 AND rm.deleted=b'0'
   AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN coupon_template_resource_seed s
  ON m.id=s.id OR (m.realm='merchant' AND s.permission<>'' AND m.permission=s.permission)
   OR (m.realm='merchant' AND s.path<>'' AND m.path=s.path)),1,0);
SET @coupon_template_seed_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,'merchant',name,permission,type,sort,parent_id,path,'',component,'',
 0,IF(type=3,b'0',b'1'),b'0',b'0',0,'sql-coupon-template-resource-seed','sql-coupon-template-resource-seed',b'0'
FROM coupon_template_resource_seed;
SET @coupon_template_seed_count=ROW_COUNT();
INSERT INTO coupon_template_resource_guard SELECT IF(@coupon_template_seed_count=5,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
SELECT 0,0,@coupon_template_seed_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V024-coupon-templates','runId',@coupon_template_seed_run,
  'realm','merchant','permission',permission),'' FROM coupon_template_resource_seed;
INSERT INTO coupon_template_resource_guard SELECT IF(ROW_COUNT()=5,1,0);
COMMIT;
SELECT 'COUPON_TEMPLATE_RESOURCES_SEEDED' AS result,@coupon_template_seed_count AS menus,@coupon_template_seed_run AS run_id;
