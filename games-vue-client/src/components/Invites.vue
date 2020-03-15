<template>
  <div class="invites">
    <v-card class="invites-recieved" v-if="invites.length > 0">
      <v-list two-line>
        <div v-for="(invite, index) in invites" :key="invite.host" >
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
    <v-dialog v-model="complexInviteDialog" max-width="42%" persistent>
      <InviteComplex />
    </v-dialog>
    <v-dialog v-model="inviteWaiting.dialogActive" max-width="42%" persistent>
      <v-card class="invites-sent">
        <v-card-title>
          Invite
        </v-card-title>
        <v-card-text v-if="inviteWaiting.waitingFor.length > 0">
          Waiting for response from
          <v-chip v-for="username in inviteWaiting.waitingFor" :key="username">{{ username }}</v-chip>
        </v-card-text>
        <v-card-text v-if="inviteWaiting.accepted.length > 0">
          Accepted:
          <span v-for="username in inviteWaiting.accepted" :key="username">
            {{ username }}
          </span>
        </v-card-text>
        <v-card-text v-if="inviteWaiting.declined.length > 0">
          Declined:
          <span v-for="username in inviteWaiting.declined" :key="username">
            {{ username }}
          </span>
        </v-card-text>
        <v-card-text v-if="inviteWaiting.waitingFor.length == 0 && inviteWaiting.declined.length == 0">
          <v-text-field label="Share invite link" placeholder="Invite link" readonly :value="inviteURL"></v-text-field>
          <v-btn color="info" v-clipboard="() => inviteURL">Copy link</v-btn>
        </v-card-text>
        <v-card-actions>
          <v-btn v-if="!inviteWaiting.cancelled"
            color="error" @click="inviteCancel(inviteWaiting)">Cancel invite</v-btn>
          <v-btn v-if="inviteWaiting.cancelled"
            color="warning" @click="resetInviteWaiting()">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
<script>
import Socket from "../socket";
import supportedGames from "@/supportedGames";
import { mapState } from 'vuex';
import InviteComplex from "@/components/InviteComplex";

export default {
  name: "Invites",
  props: ["complexDialogGameType"],
  data() {
    return {
      complexInviteDialog: false
    }
  },
  components: {
    InviteComplex
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
          gameId: e.gameId, // needed for route URL
          gameInfo: {
            gameType: e.gameType,
            players: e.players,
            gameId: e.gameId,
            yourIndex: e.yourIndex
          }
        }
      });
    }
  },
  created() {
    Socket.$on("type:GameStarted", this.gameStartedMessage);
  },
  watch: {
    complexInvite(newState) {
      if (newState != null) {
        this.complexInviteDialog = true;
      }
    }
  },
  computed: {
    ...mapState('lobby', {
      invites: state => state.invites,
      inviteWaiting: state => state.inviteWaiting,
      complexInvite: state => state.complexInvite
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
