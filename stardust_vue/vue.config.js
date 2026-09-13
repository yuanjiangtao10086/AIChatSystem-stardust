const { defineConfig } = require("@vue/cli-service");
module.exports = defineConfig({
  transpileDependencies: true,
  parallel: false,
  configureWebpack: {
    cache: false,
    // katex 是 markdown 数学公式的同步渲染依赖（katex.renderToString 无法
    // 异步化），自身就有 ~550 KiB，必然超过 webpack 默认的 244 KiB 通用预算。
    // 这里按项目实际基线设置预算：既消除噪音，又能在体积真正劣化时告警。
    performance: {
      hints: process.env.NODE_ENV === "production" ? "warning" : false,
      maxAssetSize: 700 * 1024,
      maxEntrypointSize: 1024 * 1024,
    },
  },
  chainWebpack: (config) => {
    config.module
      .rule("js")
      .use("babel-loader")
      .tap((options) => ({ ...options, cacheDirectory: false }));
    config.plugin("eslint").tap((args) => {
      args[0].cache = false;
      return args;
    });
  },
  devServer: {
    proxy: {
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: false,
      },
    },
  },
});
