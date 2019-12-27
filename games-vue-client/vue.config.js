let branch = process.env.VUE_APP_GIT_BRANCH;
if (branch === "master") {
  branch = "";
}
if (process.env.NODE_ENV === "development") {
  branch = "";
}

module.exports = {
  runtimeCompiler: true,
  transpileDependencies: [
    "vuetify"
  ],
  devServer: {
    host: "0.0.0.0",
    disableHostCheck: true
  },
  chainWebpack: config => {
    config.externals({
      "uttt-js": "uttt-js",
      klog: "klog"
    });
  },
  publicPath: "/" + branch
};
