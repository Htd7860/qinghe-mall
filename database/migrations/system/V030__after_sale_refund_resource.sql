-- DEC-046/065/066. Financial refund permission metadata only.
-- No role/package grants, tenant changes, payment calls or business refunds.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE after_sale_refund_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
START TRANSACTION;
INSERT INTO after_sale_refund_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @refund_resource_actor,@refund_resource_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO after_sale_refund_resource_guard SELECT IF(
 EXISTS(SELECT 1 FROM system_users WHERE id=@refund_resource_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@refund_resource_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE tenant_id=0 AND user_id=@refund_resource_actor AND role_id=@refund_resource_role AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.tenant_id=0 AND rm.role_id=@refund_resource_role AND rm.deleted=b'0' AND m.realm='platform'
   AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=250000 AND realm='merchant' AND type=1 AND parent_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=250300 AND realm='merchant' AND type=2 AND parent_id=250000
  AND path='/admin/after-sales' AND permission='trade:after-sale:query' AND status=0 AND deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu WHERE id=250303 OR (realm='merchant' AND permission='trade:after-sale:refund')),1,0);
SET @refund_resource_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
VALUES(250303,'merchant','资金退款','trade:after-sale:refund',3,3,250300,'','','','',
 0,b'0',b'0',b'0',0,'sql-after-sale-refund-resource-seed','sql-after-sale-refund-resource-seed',b'0');
INSERT INTO after_sale_refund_resource_guard SELECT IF(ROW_COUNT()=1,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,from_version,to_version,details,trace_id)
VALUES(0,0,@refund_resource_actor,'MENU.SEED',250303,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant','actorContext','designated-platform-initializer',
  'batch','V030-after-sale-refund-resource','runId',@refund_resource_run,
  'realm','merchant','permission','trade:after-sale:refund','hidden',TRUE,'grantsChanged',FALSE),'');
INSERT INTO after_sale_refund_resource_guard SELECT IF(ROW_COUNT()=1,1,0);
COMMIT;
SELECT 'AFTER_SALE_REFUND_RESOURCE_SEEDED',@refund_resource_run;
