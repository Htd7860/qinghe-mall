import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router";
import "./style.css";
import "./management.css";
import { initializeData, ui } from "./data/store";

initializeData().then(() => {
  const app = createApp(App);
  app.config.errorHandler = () => {
    ui.error = "页面加载失败，请刷新后重试";
    console.error("frontend.render.failure");
  };
  router.onError(() => {
    ui.error = "页面加载失败，请刷新后重试";
  });
  app.use(router).mount("#app");
});
