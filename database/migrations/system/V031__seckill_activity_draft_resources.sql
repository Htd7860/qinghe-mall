-- DEC-046/065/066: merchant seckill draft resource catalog only.
-- Existing roles/packages are not expanded. Keep navigation hidden until real UI is connected.
SET NAMES utf8mb4;
SET SESSION innodb_lock_wait_timeout=10;
CREATE TEMPORARY TABLE seckill_activity_resource_guard(ok TINYINT NOT NULL CHECK(ok=1)) ENGINE=InnoDB;
CREATE TEMPORARY TABLE seckill_activity_resource_seed(
 id BIGINT NOT NULL PRIMARY KEY,name VARCHAR(50) NOT NULL,permission VARCHAR(100) NOT NULL,
 type TINYINT NOT NULL,sort INT NOT NULL,parent_id BIGINT NOT NULL,path VARCHAR(200) NOT NULL,component VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
INSERT INTO seckill_activity_resource_seed VALUES
 (240200,'秒杀活动','promotion:seckill-activity:query',2,2,240000,'/admin/seckill','SeckillAdmin'),
 (240201,'新增秒杀活动草稿','promotion:seckill-activity:create',3,1,240200,'',''),
 (240202,'修改秒杀活动草稿','promotion:seckill-activity:update',3,2,240200,'','');
START TRANSACTION;
INSERT INTO seckill_activity_resource_guard SELECT IF(@@hostname='QIANG' AND DATABASE()='qinghe_system' AND VERSION() LIKE '8.0.%',1,0);
SELECT platform_admin_user_id,platform_admin_role_id INTO @seckill_resource_actor,@seckill_resource_role
FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO seckill_activity_resource_guard SELECT IF(
 EXISTS(SELECT 1 FROM system_users WHERE id=@seckill_resource_actor AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role WHERE id=@seckill_resource_role AND tenant_id=0 AND status=0 AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_user_role WHERE tenant_id=0 AND user_id=@seckill_resource_actor AND role_id=@seckill_resource_role AND deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id
  WHERE rm.tenant_id=0 AND rm.role_id=@seckill_resource_role AND rm.deleted=b'0' AND m.realm='platform'
   AND m.permission='system:menu:create' AND m.status=0 AND m.deleted=b'0')
 AND EXISTS(SELECT 1 FROM system_menu WHERE id=240000 AND realm='merchant' AND type=1 AND parent_id=0 AND status=0 AND deleted=b'0')
 AND NOT EXISTS(SELECT 1 FROM system_menu m JOIN seckill_activity_resource_seed s
  ON m.id=s.id OR (m.realm='merchant' AND s.permission<>'' AND m.permission=s.permission)
   OR (m.realm='merchant' AND s.path<>'' AND m.path=s.path)),1,0);
SET @seckill_resource_run=UUID();
INSERT INTO system_menu(id,realm,name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,auth_version,creator,updater,deleted)
SELECT id,'merchant',name,permission,type,sort,parent_id,path,'',component,'',
 0,b'0',b'0',b'0',0,'sql-seckill-activity-resource-seed','sql-seckill-activity-resource-seed',b'0'
FROM seckill_activity_resource_seed;
SET @seckill_resource_count=ROW_COUNT();
INSERT INTO seckill_activity_resource_guard SELECT IF(@seckill_resource_count=3,1,0);
INSERT INTO system_management_audit(tenant_id,actor_tenant_id,actor_user_id,action,target_id,from_version,to_version,details,trace_id)
SELECT 0,0,@seckill_resource_actor,'MENU.SEED',id,-1,0,
 JSON_OBJECT('source','user-authorized-direct-sql','appliedBy','assistant','actorContext','designated-platform-initializer',
  'batch','V031-seckill-activity-draft-resources','runId',@seckill_resource_run,
  'realm','merchant','permission',permission,'hidden',TRUE,'grantsChanged',FALSE),''
FROM seckill_activity_resource_seed;
INSERT INTO seckill_activity_resource_guard SELECT IF(ROW_COUNT()=3,1,0);
COMMIT;
SELECT 'SECKILL_ACTIVITY_DRAFT_RESOURCES_SEEDED',@seckill_resource_count,@seckill_resource_run;
