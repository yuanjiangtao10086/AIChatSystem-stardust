module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    "plugin:vue/vue3-essential",
    "eslint:recommended",
    "@vue/typescript/recommended",
    "plugin:prettier/recommended",
  ],
  parserOptions: {
    ecmaVersion: 2020,
  },
  rules: {
    // 生产构建仍禁止 console.log，但保留刻意的告警/错误诊断输出
    // （如 SSE 解析异常、自动命名失败的 console.warn）。
    "no-console":
      process.env.NODE_ENV === "production"
        ? ["warn", { allow: ["warn", "error"] }]
        : "off",
    "no-debugger": process.env.NODE_ENV === "production" ? "warn" : "off",
  },
};
