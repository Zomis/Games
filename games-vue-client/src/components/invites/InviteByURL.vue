<template>
  <div class="invite-by-url">
    <AuthChoice
      :server="server"
      :on-authenticated="onAuthenticated"
    />

    <Invites />
  </div>
</template>

<script>
import Socket from "@/socket";
import AuthChoice from "@/components/AuthChoice";
import Invites from "./Invites";

export default {
  name: "ServerSelection",
  props: ["inviteId", "server"],
  components: { AuthChoice, Invites },
  methods: {
    onAuthenticated(authentication) {
      console.log(authentication);
      Socket.send(
        `{ "type": "InviteResponse", "invite": "${
          this.inviteId
        }", "accepted": true }`
      );
    }
  }
};
</script>
