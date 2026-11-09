# 青禾多租户商城

面向多商家的 SaaS 电商项目，提供平台运营、商家管理、消费者商城三个入口，包含商品、交易、履约售后、秒杀与商品搜索。

## 技术栈

- 后端：Java 8、Spring Boot 2.7、Spring Cloud / Spring Cloud Alibaba、OpenFeign、Spring Security、MyBatis-Plus。
- 存储与后台组件：MySQL 8、Redis、RocketMQ、Elasticsearch 7.17、XXL-JOB。
- 前端：Vue 3、TypeScript、Vite。

## 工程结构

| 目录 | 职责 |
| --- | --- |
| `qinghe-dependencies` | BOM 与依赖版本管理 |
| `qinghe-framework` | 通用返回、异常、Web、安全、MyBatis、Redis、多租户、任务等基础组件 |
| `qinghe-gateway` | 网关路由、Bearer 认证、来源限流及统一错误响应 |
| `qinghe-module-system` | 账号、Token、租户套餐、角色菜单与权限 |
| `qinghe-module-member` | 消费者账号、登录及地址 |
| `qinghe-module-product` | 商品、分类品牌、库存与 ES 搜索同步 |
| `qinghe-module-promotion` | 优惠券、秒杀活动、库存预占与恢复 |
| `qinghe-module-trade` | 购物车、结算、订单、履约、售后与消息处理 |
| `qinghe-module-pay` | 支付、退款及结果核对 |
| `qinghe-module-infra` | 文件配置与文件服务 |
| `frontend` | 三端前端工程 |
| `database` | 按服务划分的数据库迁移与初始化脚本 |
| `third-party` | 复用代码的许可与来源记录 |

## 主要实现

- 多租户与 RBAC：租户上下文与 MyBatis-Plus 多租户条件；用户、角色、菜单按钮授权；套餐约束商家可授权范围。
- 认证：随机 access token / refresh token、服务端校验与撤销、登录来源限流和账号失败次数保护。
- 秒杀：Redis Lua 库存预占与限购；申请、库存占用与本地消息事务保存；MQ 异步建单、消费幂等及明确关闭后的补偿。
- 交易售后：按业务编号去重，支付关闭确认与订单条件更新，退款未知结果查询恢复及库存回补幂等。
- 商品搜索：关键词、分类、品牌和价格组合检索；external 版本控制；持久同步进度与离线重建。
- 后台恢复：XXL-JOB 调度消息补发、超时核对、库存恢复与搜索同步。

## 本地使用

环境配置、构建入口及数据库说明见 [本地开发说明](docs/SETUP.md)。前端入口见 [frontend/README.md](frontend/README.md)。

数据库密码、内部服务凭据、支付密钥等通过环境变量提供；本仓库不包含本机运行数据或真实账号。支付相关既有验证使用 Mock 客户端，真实支付渠道需另外配置验证。

## 源码来源

部分功能择取并适配自 [yudao-cloud](https://github.com/YunaiV/yudao-cloud) 与 [mall](https://github.com/macrozheng/mall)。对应许可、原作者与来源声明保留在 `third-party`、`frontend/third-party` 及相关源码中。
