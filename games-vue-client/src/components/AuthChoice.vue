<template>
  <v-container>
    <h2>Sign in with</h2>
    <v-card
      class="d-flex justify-center mb-4"
      flat
      tile
    >
      <v-card
        outlined
        class="d-flex flex-column pa-8"
      >
        <v-btn
          large
          color="info"
          class="mb-4 auth-btn"
          @click="authenticate('github')"
        >
          Github
        </v-btn>

        <div
          id="google-btn"
          class="mb-4 auth-btn"
          @click="authenticate('google')"
        >
          <span class="icon" />
          <span class="buttonText">Google</span>
        </div>
        
        <v-btn
          large
          color="info"
          class="auth-btn"
          @click="authenticateGuest()"
        >
          <v-avatar
            :size="24"
            class="icon"
            :style="{ 'margin-right': '4px' }"
          >
            <img
              :src="`https://www.gravatar.com/avatar/123?s=128&d=identicon`"
              alt="Login as guest"
            >
          </v-avatar>
          <span>Guest</span>
        </v-btn>
      </v-card>
    </v-card>
  </v-container>
  <!--
    {{ getToken() }}
    {{ lastUsedProvider }}
    {{ $auth.isAuthenticated() }}
    -->
</template>
<script>
import Socket from "../socket";

export default {
  name: "AuthChoice",
  props: ["server", "onAuthenticated", "autoLogin"],
  data() {
    return {
      auth: { provider: null },
      lastUsedProvider: null,
    };
  },
  methods: {
    authenticateGuest: function () {
      localStorage.authCookie = null;
      this.auth = { provider: "guest" };
      Socket.connect(this.server);
    },
    authenticate: function (provider) {
      this.auth = { provider: provider };
      localStorage.lastUsedProvider = provider;
      let controller = this;
      this.$auth.authenticate(provider).then(function () {
        console.log("Authenticated with " + provider);
        Socket.connect(controller.server);
      });
    },
    getToken() {
      console.log("Checking token", this.auth)
      if (this.auth.provider !== 'guest' && this.$auth.isAuthenticated()) {
        return this.$auth.getToken();
      }
      if (this.auth.provider === 'guest' && localStorage.authCookie && localStorage.authCookie !== "null") {
        return "cookie:" + localStorage.authCookie;
      }
      return false;
    },
    connected: function () {
      console.log("WebSocket Connected!");
      let token = this.getToken();
      Socket.route(`auth/${this.auth.provider}`, { token: token });
    },
    authenticated: function (e) {
      // Authenticated by server
      if (e.cookie) {
        localStorage.authCookie = e.cookie;
        localStorage.lastUsedProvider = "guest";
      }
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
<style>
#google-btn {
  display: inline-block;
  background-color: #4285F4;
}
#google-btn span.icon {
  background: url('https://developers.google.com/identity/sign-in/g-normal.png') transparent 5px 50% no-repeat;
}
#google-btn span.buttonText {
  font-size: 14px;
  font-weight: bold;
  font-family: 'Roboto', sans-serif;
}

.auth-btn {
  border-radius: 5px;
  white-space: nowrap;
}
.auth-btn:hover {
  cursor: pointer;
}
.auth-btn .icon {
  display: inline-block;
  vertical-align: middle;
  width: 42px;
  height: 42px;
}
.auth-btn .buttonText {
  display: inline-block;
  vertical-align: middle;
  color: #fff;
  padding-left: 42px;
  padding-right: 42px;
}
</style>
