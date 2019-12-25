<template>
  <div class="authchoice">
    <h2>Choose authentication type</h2>
    <v-btn color="info" @click="authenticate('github')">Login with Github</v-btn>
    <v-btn color="info" @click="authenticateGuest()">Login as Guest</v-btn>
    {{ getToken() }}
    {{ lastUsedProvider }}
    {{ $auth.isAuthenticated() }}
  </div>
</template>
<script>
import Socket from "../socket";

export default {
  name: "AuthChoice",
  props: ["server", "onAuthenticated", "autoLogin"],
  data() {
    return {
      auth: { provider: null },
      lastUsedProvider: null
    };
  },
  methods: {
    authenticateGuest: function() {
      this.auth = { provider: "guest" };
      Socket.connect(this.server);
    },
    authenticate: function(provider) {
      this.auth = { provider: provider };
      localStorage.lastUsedProvider = provider;
      let controller = this;
      this.$auth.authenticate(provider).then(function() {
        console.log("Authenticated with " + provider);
        Socket.connect(controller.server);
      });
    },
    getToken() {
      if (this.$auth.isAuthenticated()) {
        return this.$auth.getToken();
      }
      return false;
    },
    connected: function() {
      console.log("WebSocket Connected!");
      let token = this.getToken();
      Socket.send(
        `v1:{ "type": "Auth", "provider": "${
          this.auth.provider
        }", "token": "${token}" }`
      );
    },
    authenticated: function(e) {
      this.onAuthenticated(e);
    }
  },
  mounted() {
    this.lastUsedProvider = localStorage.lastUsedProvider;
    this.auth.provider = this.lastUsedProvider;
    if (this.lastUsedProvider != null && this.autoLogin) {
      Socket.connect(this.server);
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
