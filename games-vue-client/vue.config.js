const {gitDescribeSync} = require('git-describe');
let isAWS = process.env.MY_BUILD_TARGET === "AWS"
if (isAWS) {
  process.env.VUE_APP_BUILD_TIME = new Date().toISOString()
  process.env.VUE_APP_BUILD_NUMBER = 0
  process.env.VUE_APP_GIT_COMMIT = gitDescribeSync().hash
  process.env.VUE_APP_GIT_BRANCH = "main"
}

let branch = process.env.VUE_APP_GIT_BRANCH;
if (branch === "main") {
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
  outputDir: isAWS ? ".codedeploy/dist" : "dist",
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
