<template>
  <AuthChoice :server="server" :onAuthenticated="onAuthenticated" />
</template>

<script>
import Socket from "../socket";
import AuthChoice from "./AuthChoice";

export default {
  name: "ServerSelection",
  props: ["inviteId", "server"],
  components: { AuthChoice },
  methods: {
    onAuthenticated(authentication) {
      console.log("");
      Socket.send(
        `{ "type": "InviteResponse", "invite": "${
          this.inviteId
        }", "accepted": true }`
      );
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
