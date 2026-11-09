-- DEC-046/065/066: one hidden merchant publishing lifecycle permission, no automatic grants.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_publish_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
START TRANSACTION;
INSERT INTO seckill_publish_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @seckill_publish_actor,@seckill_publish_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO seckill_publish_resource_guard SELECT IF(
 EXISTS(SELECT 1 FROM system_users WHERE id=@seckill_publish_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@seckill_publish_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE tenant_id=0 AND user_id=@seckill_publish_actor AND role_id=@seckill_publish_role AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE rm.tenant_id=0
  AND rm.role_id=@seckill_publish_role AND rm.deleted=b'0' AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=240200 AND realm='merchant' AND type=2 AND parent_id=240000
  AND permission='promotion:seckill-activity:query' AND path='/admin/seckill' AND status=0 AND visible=b'0' AND deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu WHERE id=240203 OR (realm='merchant' AND permission='promotion:seckill-activity:publish')),1,0);
SET @seckill_publish_resource_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
VALUES(240203,'merchant','秒杀发布与原号恢复','promotion:seckill-activity:publish',3,3,240200,'','','','',
 0,b'0',b'0',b'0',0,'sql-seckill-publish-resource-seed','sql-seckill-publish-resource-seed',b'0');
INSERT INTO seckill_publish_resource_guard SELECT IF(ROW_COUNT()=1,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,from_version,to_version,details,trace_id)
VALUES(0,0,@seckill_publish_actor,'MENU.SEED',240203,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant','actorContext','designated-platform-initializer',
  'batch','V032-seckill-publish-resource','runId',@seckill_publish_resource_run,'realm','merchant',
  'permission','promotion:seckill-activity:publish','hidden',TRUE,'grantsChanged',FALSE),'');
INSERT INTO seckill_publish_resource_guard SELECT IF(ROW_COUNT()=1,1,0);
COMMIT;
SELECT 'SECKILL_PUBLISH_RESOURCE_SEEDED',@seckill_publish_resource_run;
