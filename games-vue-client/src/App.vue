<template>
  <v-app>
    <v-app-bar app>
      <router-link to="/">
        <v-toolbar-title v-text="title"></v-toolbar-title>
      </router-link>
      <v-spacer></v-spacer>
      <v-toolbar-items>
        <v-btn text to="/">Home</v-btn>
        <v-btn text @click="logout()">Logout</v-btn>
      </v-toolbar-items>
    </v-app-bar>
    <v-content>
      <router-view :key="$route.path" />
    </v-content>
    <v-footer fixed app>
      <span>&copy; 2018 Zomis' Games - <a href="https://github.com/Zomis/Server2">Github</a></span>
    </v-footer>
  </v-app>
</template>

<script>
import Socket from "./socket";
import store from "./store";

export default {
  name: "App",
  store,
  data() {
    return {
      title: "Zomis Games"
    };
  },
  methods: {
    logout() {
      Socket.disconnect();
      this.$router.push({
        name: "ServerSelection",
        params: { logout: true }
      });
    }
  }
};
</script>

<style>
#app {
  font-family: "Avenir", Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
