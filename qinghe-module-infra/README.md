# qinghe-module-infra

文件能力移植自本地 yudao-cloud infra。原作者与 MIT 许可见 ../third-party/yudao-cloud/。

## 当前源码

独立 Servlet 应用 InfraServerApplication，默认127.0.0.1:8081，使用独立qinghe_infra数据库。
公共Security starter通过原Bearer向system确认身份和权限，不恢复Cookie/Session、不信任自报login-user。
当前支持本地图片存储、文件元数据、默认10MiB上限、可信租户/上传者/用途、本人分页/详情/下载/删除和公开图片读取。
前端真实管理页有按权限显示的“图片素材”弹窗，无模拟数据回退。

## 数据与配置

本机infra_file_config/infra_file及V002的infra_file_reference已创建；LOCAL主配置id1。不要重复运行V001覆盖已有库，MySQL DDL隐式提交。
主配置根路径D:/aaa/a-yudao/mall-saas/runtime/files，domain=http://127.0.0.1:8088；实际上传时才写文件。
此批没有创建该目录或写入图片。正式部署须配置独立持久存储及域名，当前配置管理接口不对外开放。

- QINGHE_DB_PASSWORD：沿用已有用户环境密码，不写文件或命令行。
- QINGHE_DB_USERNAME：本机开发默认root，未创建/修改数据库账号权限。
- QINGHE_INFRA_DB_URL：默认127.0.0.1:3306/qinghe_infra。
- SYSTEM_BASE_URL：默认http://127.0.0.1:8080。
- INFRA_PORT：默认8081；Gateway的INFRA_BASE_URL默认http://127.0.0.1:8081。
- local profile按NACOS_NAMESPACE注册到现有Nacos、QINGHE_MALL组；本批未写Nacos服务端配置。
- qinghe.file.upload.max-bytes默认10485760；调整时须同步multipart请求上限与前端提示/限制。

system V017已新增两端文件资源，并为可信平台管理员引用对应的角色补3条资源绑定、推进版本和审计。
旧商家套餐与角色未自动扩权。商家需在套餐显式选取“文件素材”及操作，再按现有流程分配角色权限。

## 用户自行执行的命令

本批没有运行以下构建、启动命令或测试。旧system/Gateway jar也需要重建重启，才能提供新增RPC和路由。

```powershell
# D:/aaa/a-yudao/mall
mvn -pl "qinghe-module-infra/qinghe-module-infra-server,qinghe-module-system/qinghe-module-system-server,qinghe-gateway" -am "-Dmaven.test.skip=true" package
java -jar .\qinghe-module-infra\qinghe-module-infra-server\target\qinghe-module-infra-server-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

前端本批也未执行typecheck/build。

## HTTP

后台前缀/admin-api或/platform-api，均核对ADMIN和对应租户。

| 方法 | 路径（接后台前缀） | 权限 |
|---|---|---|
| POST | /infra/file/upload，multipart字段file、purpose | infra:file:create |
| GET | /infra/file/page，pageNo/pageSize | infra:file:query |
| GET | /infra/file/get?id=... | infra:file:query |
| GET | /infra/file/content?id=... | infra:file:query，且本人 |
| DELETE | /infra/file/delete?id=... | infra:file:delete，且本人 |

product-image、store-logo公开，aftersale-evidence私有；平台仅允许store-logo。
消费者HTTP尚未开放，不能因存在MEMBER枚举称消费者已接通。
返回{id,name,type,size,purpose,url,contentPath,createTime}；私有url为null，通过contentPath主动携带Bearer下载。
公开GET结构为/public-api/infra/file/{configId}/get/tenant/{tenantId}/{purpose}/{date}/{filename}，只接受公开用途且必须命中未删除元数据，无任意/get/**路径读取。

system内部POST /internal-api/auth/permission/check只接permissions，用当前Bearer主体，不接目标userId/tenantId；Gateway不路由它。
认证RPC配置1s连接、3s读取、32并发、不自动重试、不重定向、不输出Feign凭据日志；这些行为尚未实测。

## 未验证与后续边界

未编译/JUnit/guard/真实上传下载删除/认证RPC/浏览器回归，静态检查不等于运行通过。
文件与DB不是同一事务；创建保持NOT_SUPPORTED。删除现用REQUIRES_NEW、10秒事务锁文件行并检查引用，再删对象与记录；结果未知不盲删可能已提交的随机对象。
可能留孤儿文件；物理删除后DB失败需按原id核查重试。当前商品分类/品牌增加tenant+product-image+本人或同资源既有绑定检查，引用存在即拒绝素材删除。内部POST /internal-api/infra/file/bind不经Gateway路由。引用保守保留，业务更新/删除、超时/回滚后的权威清理仍待实现；售后关联尚未接入。
文件不存在、跨租户或非本人私有读取统一404；其余领域错误沿用非零CommonResult。
