<template>
  <div class="invites">
    <v-card class="invites-recieved" v-if="invites.length > 0">
      <v-list two-line>
        <div v-for="(invite, index) in invites" :key="invite.inviteId" >
          {{ invite.host }} invites you to play {{ invite.game }}
          <div class="invite-accept-options" v-if="!invite.cancelled && invite.response === null">
            <v-btn color="success" @click="inviteResponse(invite, true)">Accept</v-btn>
            <v-btn color="error" @click="inviteResponse(invite, false)">Decline</v-btn>
          </div>
          <div class="invite-response" v-if="invite.response !== null">
            <v-btn color="success" disabled v-if="invite.response">Accepted</v-btn>
            <v-btn color="error" disabled v-if="!invite.response">Declined</v-btn>
          </div>
          <v-btn v-if="invite.cancelled"
            color="warning" @click="invites.splice(index, 1)">Invite cancelled</v-btn>
        </div>
      </v-list>
    </v-card>
<!--    <v-dialog v-model="complexInviteDialog" max-width="42%" persistent>
      <InviteComplex />
    </v-dialog> -->
  </div>
</template>
<script>
import Socket from "@/socket";
import supportedGames from "@/supportedGames";
import { mapState } from 'vuex';
//import InviteComplex from "./InviteComplex";

export default {
  name: "Invites",
  props: ["complexDialogGameType"],
  data() {
    return {
      complexInviteDialog: false
    }
  },
  components: {
//    InviteComplex
  },
  methods: {
    inviteLink(gameType) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": [] }`
      );
    },
    inviteCancel(invite) {
//      this.$store.dispatch("invites/cancel", invite.inviteId);
      Socket.route(`invites/${invite.inviteId}/cancel`);
    },
    inviteResponse: function(invite, accepted) {
      Socket.route(`invites/${invite.inviteId}/respond`, { accepted: accepted });
      this.$set(invite, "response", accepted);
    },
    gameStartedMessage: function(e) {
      let game = supportedGames.games[e.gameType]
      let routeName = game.routeName || e.gameType;
      this.$router.push({
        name: routeName,
        params: {
          gameId: e.gameId
        }
      });
    }
  },
  created() {
    Socket.$on("type:GameStarted", this.gameStartedMessage);
  },
  watch: {
    inviteStep(newState) {
      this.complexInviteDialog = newState > 0
    }
  },
  computed: {
    ...mapState('lobby', {
      invites: state => state.invites,
      inviteStep: state => state.inviteWaiting.inviteStep,
      inviteWaiting: state => state.inviteWaiting
    }),
    inviteURL() {
      if (this.inviteWaiting.inviteId === null) {
        return null;
      }
      return (
        document.location.origin +
        "#/invite/" +
        this.inviteWaiting.inviteId +
        "?server=" +
        Socket.getServerURL()
      );
    }
  },
  beforeDestroy() {
    Socket.$off("type:GameStarted", this.gameStartedMessage);
  }
};
</script>
