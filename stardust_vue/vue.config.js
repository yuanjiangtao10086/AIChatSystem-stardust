const { defineConfig } = require("@vue/cli-service");
module.exports = defineConfig({
  transpileDependencies: true,
  parallel: false,
  configureWebpack: {
    cache: false,
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
