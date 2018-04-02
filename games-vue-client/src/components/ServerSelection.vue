<template>
  <div class="server-selection">
    <h2>Choose server</h2>
    <select v-model="chosenServer">
      <option v-for="server in serverOptions" :value="server.url">{{ server.name }}</option>
    </select>

    <h2>Choose authentication type</h2>
    <button @click="authenticate('github')">Login with Github</button>
    <button @click="authenticateGuest()">Login as Guest</button>

    <p>The server "zomis" should be up and running always.<br />
      If you want to start a local server, <a href="https://github.com/Zomis/Server2">clone my project on GitHub</a>.<br />
      You can also <router-link :to="'/games/UR/1/'">play Royal Game of UR without connecting to a server</router-link>
    </p>
  </div>
</template>

<script>
import Socket from "../socket";

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
      chosenServer: "zomis"
    };
  },
  methods: {
    authenticateGuest: function() {
      this.auth = { provider: "guest" };
      Socket.connect(this.chosenServer);
    },
    authenticate: function(provider) {
      this.auth = { provider: provider };
      let controller = this;
      this.$auth.authenticate(provider).then(function() {
        console.log("Authenticated with " + provider);
        Socket.connect(controller.chosenServer);
      });
    },
    connected: function(e) {
      Socket.send(
        `v1:{ "type": "Auth", "provider": "${this.auth.provider}", "token": "${
          this.token
        }" }`
      );
    },
    authenticated: function(e) {
      Socket.loginName = e.name;
      this.$router.push("/connected");
    }
  },
  computed: {
    token: function() {
      if (this.$auth.isAuthenticated()) {
        return this.$auth.getToken();
      }
      return false;
    }
  },
  created() {
    Socket.$on("connected", this.connected);
    Socket.$on("type:Auth", this.authenticated);
  },
  beforeDestroy() {
    Socket.$off("connected", this.connected);
    Socket.$off("type:Auth", this.authenticated);
  }
};
</script>

<style scoped>
</style>
