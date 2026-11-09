-- DEC-046/065/066. Wire the real merchant aftersale view into existing navigation.
-- No menu creation, package changes, role grants or business decisions.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_navigation_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
START TRANSACTION;
INSERT INTO after_sale_navigation_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @after_sale_nav_actor,@after_sale_nav_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO after_sale_navigation_guard SELECT IF(
 EXISTS(SELECT 1 FROM system_users WHERE id=@after_sale_nav_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@after_sale_nav_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE tenant_id=0 AND role_id=@after_sale_nav_role AND user_id=@after_sale_nav_actor AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.tenant_id=0 AND rm.role_id=@after_sale_nav_role AND rm.deleted=b'0' AND m.realm='platform'
   AND m.permission='system:menu:update' AND m.status=0 AND m.deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=250000 AND realm='merchant' AND parent_id=0 AND type=1 AND status=0 AND visible=b'1' AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=250300 AND realm='merchant' AND parent_id=250000 AND type=2 AND status=0
  AND path='/admin/after-sales' AND permission='trade:after-sale:query' AND auth_version=0 AND visible=b'0' AND deleted=b'0'),1,0);
SET @after_sale_nav_run=UUID();
UPDATE system_menu SET visible=b'1',auth_version=auth_version+1,updater='sql-after-sale-navigation-seed'
WHERE id=250300 AND realm='merchant' AND auth_version=0 AND visible=b'0' AND deleted=b'0';
INSERT INTO after_sale_navigation_guard SELECT IF(ROW_COUNT()=1,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,from_version,to_version,details,trace_id)
VALUES(0,0,@after_sale_nav_actor,'MENU.UPDATE',250300,0,1,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant','actorContext','designated-platform-initializer',
  'batch','V029-after-sale-navigation','runId',@after_sale_nav_run,'fromVisible',FALSE,'toVisible',TRUE,'grantsChanged',FALSE),'');
INSERT INTO after_sale_navigation_guard SELECT IF(ROW_COUNT()=1,1,0);
COMMIT;
SELECT 'AFTER_SALE_NAVIGATION_ENABLED',@after_sale_nav_run;
