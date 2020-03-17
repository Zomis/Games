<template>
  <v-card>
    <h1>Testing invites</h1>
    <InviteComplex v-if="ready" />
  </v-card>
</template>

<script>
import InviteComplex from "@/components/InviteComplex";

export default {
  name: "TestScreen",
  components: { InviteComplex },
  data() {
    return { ready: false }
  },
  mounted() {
    this.$store.commit("onSocketMessage", { type: "Auth", playerId: "self", name: "Myself" })
    this.$store.commit("lobby/createInvite", "Artax")
    this.$store.commit("lobby/inviteStep", 2)
    this.$store.dispatch("onSocketMessage", { type: "Lobby", users: { Artax: [
      { id: "self", name: "Myself" },
      { id: "abc", name: "Someone" },
      { id: "def", name: "#AI_Random" },
      { id: "ghi", name: "#AI_Easy" },
      { id: "jkl", name: "#AI_Normal" }
    ] } })
    this.$store.dispatch("onSocketMessage", { type: "InviteWaiting", inviteId: "12345" })
    this.$store.dispatch("onSocketMessage", { type: "InviteStatus", playerId: "abc", inviteId: "12345" })
    this.ready = true
  },
  methods: {

  }
}
</script>
