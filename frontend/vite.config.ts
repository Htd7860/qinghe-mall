import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  const upstream = env.DEV_API_UPSTREAM;
  if (upstream && !/^https?:\/\//.test(upstream))
    throw new Error("DEV_API_UPSTREAM must be an HTTP(S) origin");
  return {
    plugins: [vue()],
    build: { sourcemap: false },
    server: {
      host: "127.0.0.1",
      port: 5180,
      strictPort: true,
      proxy: upstream
        ? {
            "/api": {
              target: upstream,
              changeOrigin: false,
              rewrite: (path) => path.replace(/^\/api/, ""),
            },
          }
        : undefined,
    },
  };
});
