# 青禾商城前端

Vue 3 + TypeScript + Vite，包含平台、商家、消费者三个入口。

```sh
npm ci
npm run dev
```

Node.js 需要 >= 22.18.0。开发入口默认 `http://127.0.0.1:5180`；设置 `DEV_API_UPSTREAM` 为实际 Gateway 地址后启动开发服务。

- `npm run build`：类型检查并生成正式构建。
- `npm test`：前端测试。
- `npm run demo`：独立演示模式，使用虚构数据与演示账号，与真实 API 模式分离。

部署配置见本目录 `Dockerfile` 与 `deploy`。图片及图标来源见 [ASSETS.md](ASSETS.md)，相关组件许可见 `third-party`。
