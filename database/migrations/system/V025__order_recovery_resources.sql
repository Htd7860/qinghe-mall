-- DEC-046/065/066. Hidden merchant permission catalog only; no new operations console/page, grants or package expansion.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE order_recovery_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE order_recovery_resource_seed(
 id BIGINT NOT NULL PRIMARY KEY,name VARCHAR(50) NOT NULL,permission VARCHAR(100) NOT NULL,
 type TINYINT NOT NULL,sort INT NOT NULL,parent_id BIGINT NOT NULL,path VARCHAR(200) NOT NULL,component VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
INSERT INTO order_recovery_resource_seed VALUES
 (250000,'交易管理','',1,50,0,'',''),
 (250100,'订单恢复查询','trade:order-recovery:query',2,1,250000,'/admin/orders/recovery','OrderRecovery'),
 (250101,'订单恢复重放','trade:order-recovery:replay',3,1,250100,'','');
START TRANSACTION;
INSERT INTO order_recovery_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @order_recovery_seed_actor,@order_recovery_seed_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO order_recovery_resource_guard
SELECT IF(@order_recovery_seed_actor>0 AND @order_recovery_seed_role>0
 AND EXISTS(SELECT 1 FROM system_users WHERE id=@order_recovery_seed_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@order_recovery_seed_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE user_id=@order_recovery_seed_actor AND role_id=@order_recovery_seed_role AND tenant_id=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.role_id=@order_recovery_seed_role AND rm.tenant_id=0 AND rm.deleted=b'0'
   AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN order_recovery_resource_seed s
  ON m.id=s.id OR (m.realm='merchant' AND s.permission<>'' AND m.permission=s.permission)
   OR (m.realm='merchant' AND s.path<>'' AND m.path=s.path)),1,0);
SET @order_recovery_seed_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,'merchant',name,permission,type,sort,parent_id,path,'',component,'',
 0,b'0',b'0',b'0',0,'sql-order-recovery-resource-seed','sql-order-recovery-resource-seed',b'0'
FROM order_recovery_resource_seed;
SET @order_recovery_seed_count=ROW_COUNT();
INSERT INTO order_recovery_resource_guard SELECT IF(@order_recovery_seed_count=3,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,
 from_version,to_version,details,trace_id)
SELECT 0,0,@order_recovery_seed_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant',
  'actorContext','designated-platform-initializer','batch','V025-order-recovery','runId',@order_recovery_seed_run,
  'realm','merchant','permission',permission,'hidden',TRUE),'' FROM order_recovery_resource_seed;
INSERT INTO order_recovery_resource_guard SELECT IF(ROW_COUNT()=3,1,0);
COMMIT;
SELECT 'ORDER_RECOVERY_RESOURCES_SEEDED' AS result,@order_recovery_seed_count AS menus,@order_recovery_seed_run AS run_id;
