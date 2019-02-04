<template>
  <div class="server-selection">
    <h2>Choose server</h2>
    <v-select :items="serverOptions" item-text="name" item-value="url" v-model="chosenServer">
    </v-select>

    <AuthChoice :server="chosenServer" :onAuthenticated="onAuthenticated" />

    <p>The server "zomis" should be up and running always.<br />
      If you want to start a local server, <a href="https://github.com/Zomis/Server2">clone my project on GitHub</a>.<br />
      You can also <router-link :to="'/games/UR/1/'">play Royal Game of UR without connecting to a server</router-link>
    </p>
  </div>
</template>

<script>
import Socket from "../socket";
import AuthChoice from "./AuthChoice";

const serverOptions = [
  {
    url: "ws://gbg.zomis.net:8082",
    name: "zomis"
  },
  {
    url: "ws://127.0.0.1:8081",
    name: "localhost-development"
  }
];

export default {
  name: "ServerSelection",
  data() {
    return {
      serverOptions: serverOptions,
      chosenServer: "ws://gbg.zomis.net:8082"
    };
  },
  components: { AuthChoice },
  methods: {
    onAuthenticated(auth) {
      this.$router.push("/connected");
    }
  }
};
</script>

<style scoped>
.input-group {
  width: 30%;
  margin: auto;
}
</style>
