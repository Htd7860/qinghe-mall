-- DEC-046/065/066. The real merchant order view is now wired.
-- Only expose existing parent/page navigation; never add package or role grants.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE order_navigation_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
START TRANSACTION;
INSERT INTO order_navigation_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @order_nav_actor,@order_nav_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO order_navigation_guard SELECT IF(
 EXISTS(SELECT 1 FROM system_users WHERE id=@order_nav_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@order_nav_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE tenant_id=0 AND role_id=@order_nav_role AND user_id=@order_nav_actor AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.tenant_id=0 AND rm.role_id=@order_nav_role AND rm.deleted=b'0' AND m.realm='platform'
   AND m.permission='system:menu:update' AND m.status=0 AND m.deleted=b'0')
 AND (SELECT COUNT(*) FROM system_menu WHERE id IN(250000,250200) AND realm='merchant' AND status=0
  AND visible=b'0' AND auth_version=0 AND deleted=b'0')=2
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=250000 AND parent_id=0 AND type=1)
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=250200 AND parent_id=250000 AND type=2 AND path='/admin/orders' AND permission='trade:order:query'),1,0);
SET @order_nav_run=UUID();
UPDATE system_menu SET visible=b'1',auth_version=auth_version+1,updater='sql-order-navigation-seed'
WHERE id IN(250000,250200) AND realm='merchant' AND auth_version=0 AND visible=b'0' AND deleted=b'0';
INSERT INTO order_navigation_guard SELECT IF(ROW_COUNT()=2,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,from_version,to_version,details,trace_id)
SELECT 0,0,@order_nav_actor,'MENU.UPDATE',id,0,1,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant','actorContext','designated-platform-initializer',
  'batch','V027-order-navigation','runId',@order_nav_run,'fromVisible',FALSE,'toVisible',TRUE,'grantsChanged',FALSE),''
FROM system_menu WHERE id IN(250000,250200) AND realm='merchant';
INSERT INTO order_navigation_guard SELECT IF(ROW_COUNT()=2,1,0);
COMMIT;
SELECT 'ORDER_NAVIGATION_ENABLED',@order_nav_run;
