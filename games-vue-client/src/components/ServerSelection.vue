<template>
  <div class="server-selection">
    <h2>Welcome to Zomis Games!</h2>

    <AuthChoice
      :server="serverInfo"
      :on-authenticated="onAuthenticated"
      :auto-login="!logout"
    />

    <h3><a href="https://discord.gg/GfXFUvc">Join us on Discord!</a></h3>

    <p>
      See <a href="https://github.com/Zomis/Games">my project on GitHub</a> for how to host your own local server.<br>
    </p>
  </div>
</template>

<script>
import Socket from "../socket";
import AuthChoice from "./AuthChoice";
const serverOptions = [
  {
    url: "wss://games.zomis.net/backend/websocket",
    name: "zomis"
  },
  {
    url: "wss://dfftey92j9.execute-api.eu-central-1.amazonaws.com/production",
    name: "aws"
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
    };
  },
  computed: {
    serverInfo() {
      const urlParams = new URLSearchParams(window.location.search);
      const chosenServer = urlParams.get('server') || "zomis";
      return this.serverOptions.find(s => s.name === chosenServer) || this.serverOptions[0];
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
