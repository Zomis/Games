<template>
  <v-list-item :key="player.id">
    <v-list-item-content>
      <v-list-item-title v-html="player.name"></v-list-item-title>
    </v-list-item-content>
    <v-list-item-action>
      <v-btn v-if="inviteable" color="info" @click="sendInvite()">+</v-btn>
      <template v-for="(state, index) in playerState">
          <v-btn :key="index" v-if="state.status === true" color="success">v</v-btn>
          <v-btn :key="index" v-if="state.status === null" color="warning">?</v-btn>
          <v-btn :key="index" v-if="state.status === false" color="error">x</v-btn>
      </template>
      <!--
          Declined: Red 'X' button
          Accepted: Green checkmark
          Pending: Yellow '?'

          Invitable: Blue '+'
      -->
    </v-list-item-action>
  </v-list-item>
</template>
<script>
import Socket from "@/socket";
import { mapState } from 'vuex';

export default {
    name: "InvitePlayer",
    props: ["invite", "player"],
    data() {
        return {}
    },
    methods: {
        sendInvite() {
            Socket.route(`invites/${this.invite.inviteId}/send`, { invite: [this.player.id] });
        }
    },
    computed: {
        ...mapState('lobby', {
            yourPlayer: state => state.yourPlayer
        }),
        isAI() {
            return this.player.name.startsWith("#AI_");
        },
        inviteIsFull() {
            if (!this.invite) return false
            let count = this.invite.invitesSent.length
            return count === this.invite.playersMax
        },
        inviteable() {
            if (this.inviteIsFull) return false
            if (this.player.id === this.yourPlayer.id) { return false }
            return this.isAI || this.playerState.length === 0
        },
        playerState() {
            if (!this.invite) { return [] }
            return this.invite.invitesSent.filter(e => e.playerId === this.player.id)
        }
    }
}
</script>