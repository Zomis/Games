<template>
  <div class="server-selection">
    <h2>Choose server</h2>
    <v-select :items="serverOptions" item-text="name" item-value="url" v-model="chosenServer">
    </v-select>

    <AuthChoice :server="serverInfo" :onAuthenticated="onAuthenticated" :autoLogin="!logout" />

    <p>The server "zomis" should be up and running always.<br />
      If you want to start a local server, <a href="https://github.com/Zomis/Server2">clone my project on GitHub</a>.<br />
      You can also <router-link to="/local">play games locally without connecting to the server (but this is boring, don't do this)</router-link>
    </p>
  </div>
</template>

<script>
import Socket from "../socket";
import AuthChoice from "./AuthChoice";

const serverOptions = [
  {
    url: "wss://games.zomis.net:42638/websocket",
    name: "zomis"
  },
  {
    url: "ws://127.0.0.1:42638/websocket",
    name: "localhost-development"
  }
];

export default {
  name: "ServerSelection",
  props: ["logout", "redirect"],
  data() {
    return {
      serverOptions: serverOptions,
      chosenServer: serverOptions[0].url
    };
  },
  computed: {
    serverInfo() {
      return this.serverOptions.find(s => s.url === this.chosenServer);
    }
  },
  components: { AuthChoice },
  mounted() {
    console.log("Mounted Redirect is:", this.redirect)
    if (Socket.isConnected()) {
      this.$router.push("/");
    }
  },
  methods: {
    onAuthenticated() {
      console.log("Redirect is:", this.redirect)
      if (this.redirect) {
        console.log("Using redirect")
        //this.redirect.resolve()
        this.$router.push(this.redirect.route)
      } else {
        this.$router.push("/");
      }
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
