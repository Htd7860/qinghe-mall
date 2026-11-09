# 本地开发说明

## 后端

根目录为 Maven 多模块工程。需要 JDK 8、Maven、MySQL 8，以及按启用功能准备的 Redis、Nacos、RocketMQ、Elasticsearch 7.17 和 XXL-JOB。

每个 `qinghe-module-*/qinghe-module-*-server/src/main/resources/application.yaml` 保存该服务配置；`application-local.yaml` 包含本地 profile 的覆盖配置。服务之间当前多采用可配置 URL 调用，不能仅因引入 Nacos 依赖就认为调用自动走服务发现。

数据库连接使用 `QINGHE_DB_USERNAME`、`QINGHE_DB_PASSWORD` 和各服务的 `QINGHE_*_DB_URL` 等环境变量。内部服务认证、短信、文件和支付相关变量应根据实际启用模块单独设置，查看对应 YAML 中的 `${...}` 占位符。

构建入口：

```sh
mvn -Dmaven.test.skip=true package
```

之后按需要启动各服务的 Boot JAR 或启动类。启动参数、端口及依赖地址以各服务 YAML 为准。此次仓库整理未重新构建或启动应用。

## 前端

需要 Node.js >= 22.18.0。

```sh
cd frontend
npm ci
npm run dev
```

开发服务默认监听 `127.0.0.1:5180`。启动前设置 `DEV_API_UPSTREAM` 为本次 Gateway 地址，例如 `http://127.0.0.1:8088`；Vite 将同源 `/api` 请求代理到网关。正式构建使用 `npm run build`。

## 数据库

`database/migrations` 按服务划分、按版本保存迁移；`database/bootstrap` 保存建库与平台账号初始化入口。它们是开发期间逐次迁移的脚本，其中部分带原本机环境、现有对象和数据状态守卫；迁移到新机器前需要审核并适配目标库，不能把全部脚本盲目拼接执行。

平台账号初始化入口为 `database/bootstrap/004_initialize_platform_admin.py`。密码在本机交互输入，不在仓库中提供默认真实账号。脚本含原本机环境守卫，使用前按目标环境审核。

## 第三方声明

后端与前端包含择取适配的第三方代码。使用和再分发时保留相关源码署名以及 `third-party`、`frontend/third-party` 中的原许可和来源记录。
