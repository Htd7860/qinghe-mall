-- DEC-046/065/066: hidden platform query/stop resources, no automatic grants.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE platform_seckill_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
START TRANSACTION;
INSERT INTO platform_seckill_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @platform_seckill_actor,@platform_seckill_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO platform_seckill_resource_guard SELECT IF(
 EXISTS(SELECT 1 FROM system_users WHERE id=@platform_seckill_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@platform_seckill_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE tenant_id=0 AND user_id=@platform_seckill_actor AND role_id=@platform_seckill_role AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE rm.tenant_id=0
  AND rm.role_id=@platform_seckill_role AND rm.deleted=b'0' AND m.realm='platform' AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=100000 AND realm='platform' AND type=1 AND parent_id=0 AND status=0 AND deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu WHERE id IN(150100,150101) OR (realm='platform' AND (permission IN('promotion:seckill-operation:query','promotion:seckill-operation:stop') OR path='/platform/seckill'))),1,0);
SET @platform_seckill_resource_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
VALUES(150100,'platform','活动监管','promotion:seckill-operation:query',2,20,100000,'/platform/seckill','','PlatformSeckill','',
 0,b'0',b'0',b'0',0,'sql-platform-seckill-resource-seed','sql-platform-seckill-resource-seed',b'0'),
 (150101,'platform','停止秒杀接单','promotion:seckill-operation:stop',3,1,150100,'','','','',
 0,b'0',b'0',b'0',0,'sql-platform-seckill-resource-seed','sql-platform-seckill-resource-seed',b'0');
INSERT INTO platform_seckill_resource_guard SELECT IF(ROW_COUNT()=2,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,from_version,to_version,details,trace_id)
SELECT 0,0,@platform_seckill_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant','actorContext','designated-platform-initializer',
  'batch','V033-platform-seckill-resources','runId',@platform_seckill_resource_run,'realm','platform',
  'permission',permission,'hidden',TRUE,'grantsChanged',FALSE),''
FROM system_menu WHERE id IN(150100,150101);
INSERT INTO platform_seckill_resource_guard SELECT IF(ROW_COUNT()=2,1,0);
COMMIT;
SELECT 'SECKILL_PUBLISH_RESOURCE_SEEDED',@platform_seckill_resource_run;
