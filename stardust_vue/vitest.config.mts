import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

// NOTE: 必须使用 .mts 扩展名。package.json 未声明 "type": "module"
// （babel.config.js / .eslintrc.js 都是 CJS，改成 module 会让 vue-cli 的
// serve/build 崩掉），因此 .ts 配置会被 esbuild 以 CJS 方式 require，
// 而 @vitejs/plugin-vue 是 ESM-only，加载配置阶段就会失败。
// .mts 会被 Vite/Vitest 明确按 ESM 加载。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.spec.ts"],
    // 当前仓库还没有任何测试用例，避免出现 "No test files found" 导致
    // npm run test 直接失败（新增用例后可去掉）。
    passWithNoTests: true,
  },
});
