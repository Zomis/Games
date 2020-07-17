<template>
  <v-app>
    <v-app-bar app>
      <router-link to="/">
        <v-toolbar-title v-text="titlePrefix"></v-toolbar-title>
      </router-link>
      <span :style="{ 'margin-left': '5px' }">{{ titleAppend }}</span>
      <template v-if="yourPlayer.loggedIn">
        <v-spacer />
        <span>Welcome, {{ yourPlayer.name }}</span>
        <v-avatar :size="48" :style="{ 'margin-left': '4px' }">
          <img
            :src="yourPlayer.picture"
            :alt="yourPlayer.name" />
        </v-avatar>
      </template>
      <v-spacer />
      <v-toolbar-items>
        <span v-if="!connection.connected">Disconnected</span>
        <span v-if="connection.connected">{{ connection.name }}</span>
        <v-btn text to="/">Home</v-btn>
        <v-btn text @click="logout()">Logout</v-btn>
      </v-toolbar-items>
    </v-app-bar>
    <v-content>
      <router-view :key="$route.path" />
    </v-content>
    <v-footer fixed app>
      <span>
          &copy; 2018-2020 Zomis' Games
           - <a href="https://github.com/Zomis/Games" target="_blank">GitHub</a>
           - <a href="https://discord.gg/GfXFUvc" target="_blank">Discord</a>
      </span>
    </v-footer>
  </v-app>
</template>

<script>
import Socket from "./socket";
import store from "./store";
import { mapState } from "vuex";

export default {
  name: "App",
  store,
  methods: {
    logout() {
      Socket.disconnect();
      this.$store.commit("lobby/logout");
      this.$router.push({
        name: "ServerSelection",
        params: { logout: true }
      });
    }
  },
  computed: {
    ...mapState({
      titlePrefix(state) { return state.titlePrefix },
      titleAppend(state) { return state.titleAppend },
      connection(state) { return state.connection }
    }),
    ...mapState("lobby", {
      yourPlayer(state) { return state.yourPlayer }
    })
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
