// The Vue build version to load with the `import` command
// (runtime-only or standalone) has been set in webpack.base.conf with an alias.
import Vue from "vue";
import App from "./App";
import router from "./router";
import "vuetify/dist/vuetify.min.css";
import vb from 'vue-babylonjs';
import vuetify from './plugins/vuetify';

Vue.use(vb);

Vue.config.productionTip = false;

/* eslint-disable no-new */
new Vue({
  el: "#app",
  router,
  components: { App },
  vuetify,
  template: "<App/>",
  mounted() {
      const { theme } = localStorage;
      this.$vuetify.theme.dark = theme === "dark";
  },
});
