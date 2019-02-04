<template>
  <div class="invites">
    <v-card class="invites-recieved">
      <v-list two-line>
        <template v-for="(invite, index) in invites" >
          {{ invite.host }} invites you to play {{ invite.game }}
          <v-btn v-if="!invite.cancelled" color="success" @click="inviteResponse(invite, true)">Accept</v-btn>
          <v-btn v-if="!invite.cancelled" color="error" @click="inviteResponse(invite, false)">Decline</v-btn>
          <v-btn v-if="invite.cancelled"
            color="warning" @click="invites.splice(index, 1)">Invite cancelled</v-btn>
        </template>
      </v-list>
    </v-card>
    <v-dialog v-model="inviteWaiting.inviteId !== null" persistent>
      <v-card class="invites-sent">
        TODO Handle response accepted false
        TODO Handle cancelled invites
        <v-chip v-if="inviteWaiting.waitingFor.length > 0">
          Waiting for response from
          <v-chip v-for="username in inviteWaiting.waitingFor" :key="username">{{ username }}</v-chip>
        </v-chip>
        <v-chip v-if="inviteWaiting.accepted.length > 0">
          Accepted:
          <v-chip v-for="username in inviteWaiting.accepted" :key="username">
            {{ username }}
          </v-chip>
        </v-chip>
        <v-chip v-if="inviteWaiting.declined.length > 0">
          Declined:
          <v-chip v-for="username in inviteWaiting.declined" :key="username">
            {{ username }}
          </v-chip>
        </v-chip>
        <v-chip v-if="inviteWaiting.waitingFor.length == 0">
          <input type="text" :value="inviteURL"></input>
        </v-chip>
        <v-btn v-if="!inviteWaiting.cancelled"
          color="error" @click="inviteResponse(inviteWaiting, false)">Cancel invite</v-btn>
        <v-btn v-if="inviteWaiting.cancelled"
          color="warning" @click="resetInviteWaiting()">Close</v-btn>
      </v-card>
    </v-dialog>
  </div>
</template>
<script>
import Socket from "../socket";
export default {
  name: "Invites",
  data() {
    return {
      invites: [],
      inviteWaiting: {
        waitingFor: [],
        inviteId: null,
        cancelled: false,
        accepted: [],
        declined: []
      }
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
    inviteLink(gameType, username) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": [] }`
      );
    },
    inviteMessage: function(e) {
      this.$set(e, "cancelled", false);
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
      responseArray.push(e.name);
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
    },
    inviteWaitingMessage: function(e) {
      this.inviteWaiting = e;
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
      this.$router.push({
        name: games[e.gameType],
        params: {
          gameType: e.gameType,
          players: e.players,
          gameId: e.gameId,
          playerIndex: e.yourIndex
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
      return document.location.origin + "#/invite/" + this.inviteWaiting.inviteId + "?server=" + Socket.getServerURL();
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
