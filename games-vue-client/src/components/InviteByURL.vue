<template>
  <div class="invite-by-url">
    <AuthChoice :server="server" :onAuthenticated="onAuthenticated" />

    <Invites />
  </div>
</template>

<script>
import Socket from "../socket";
import AuthChoice from "./AuthChoice";
import Invites from "./Invites";

export default {
  name: "ServerSelection",
  props: ["inviteId", "server"],
  components: { AuthChoice, Invites },
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
