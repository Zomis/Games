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
            color="error" @click="inviteResponse(inviteWaiting, false)">Cancel invite</v-btn>
          <v-btn v-if="inviteWaiting.cancelled"
            color="warning" @click="resetInviteWaiting()">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
<script>
import Socket from "../socket";
function emptyInvite() {
  return {
    dialogActive: false,
    waitingFor: [],
    inviteId: null,
    cancelled: false,
    accepted: [],
    declined: []
  };
}

export default {
  name: "Invites",
  data() {
    return {
      invites: [],
      inviteWaiting: emptyInvite()
    };
  },
  methods: {
    resetInviteWaiting() {
      this.inviteWaiting = {
        waitingFor: [],
        inviteId: null,
        cancelled: false,
        accepted: [],
        declined: []
      };
    },
    matchMake: function(game) {
      this.waiting = true;
      this.waitingGame = game;
      Socket.send(`v1:{ "game": "${game}", "type": "matchMake" }`);
    },
    inviteLink(gameType) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": [] }`
      );
    },
    inviteMessage: function(e) {
      this.$set(e, "cancelled", false);
      this.$set(e, "response", null);
      this.invites.push(e);
    },
    findInvite(inviteId) {
      if (this.inviteWaiting.inviteId === inviteId) {
        console.log(this.inviteWaiting);
        return this.inviteWaiting;
      } else {
        let result = this.invites.find(i => i.inviteId === inviteId);
        console.log(result);
        return result;
      }
    },
    inviteResponseMessage(e) {
      // This should only happen to the inviteWaiting at the moment
      let invite = this.findInvite(e.inviteId);
      invite.waitingFor.splice(invite.waitingFor.indexOf(e.user), 1);
      let responseArray = e.accepted ? invite.accepted : invite.declined;
      responseArray.push(e.user);
    },
    inviteCancelledMessage(e) {
      // This can be either an invite recieved or the inviteWaiting
      let invite = this.findInvite(e.inviteId);
      invite.cancelled = true;
    },
    inviteResponse: function(invite, accepted) {
      Socket.send(
        `{ "type": "InviteResponse", "invite": "${
          invite.inviteId
        }", "accepted": ${accepted} }`
      );
      this.$set(invite, "response", accepted);
    },
    inviteWaitingMessage: function(e) {
      this.inviteWaiting = e;
      this.inviteWaiting.dialogActive = true
      this.$set(this.inviteWaiting, "cancelled", false);
      this.$set(this.inviteWaiting, "accepted", []);
      this.$set(this.inviteWaiting, "declined", []);
    },
    gameStartedMessage: function(e) {
      let games = {
        UR: "RoyalGameOfUR",
        UTTT: "UTTT",
        "UTTT-ECS": "ECSGame",
        Connect4: "Connect4"
      };
      this.inviteWaiting = emptyInvite();
      this.$router.push({
        name: games[e.gameType],
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
    Socket.$on("type:InviteWaiting", this.inviteWaitingMessage);
    Socket.$on("type:InviteResponse", this.inviteResponseMessage);
    Socket.$on("type:InviteCancelled", this.inviteCancelledMessage);
    Socket.$on("type:Invite", this.inviteMessage);
    Socket.$on("type:GameStarted", this.gameStartedMessage);
  },
  computed: {
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
    Socket.$off("type:InviteWaiting", this.inviteWaitingMessage);
    Socket.$off("type:InviteResponse", this.inviteResponseMessage);
    Socket.$off("type:InviteCancelled", this.inviteCancelledMessage);
    Socket.$off("type:Invite", this.inviteMessage);
    Socket.$off("type:GameStarted", this.gameStartedMessage);
  }
};
</script>
