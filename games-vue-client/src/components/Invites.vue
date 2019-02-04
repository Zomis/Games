<template>
  <div class="invites">
    <v-card class="invites-recieved">
      <v-list two-line>
        <template v-for="invite in invites">
          {{ invite.host }} invites you to play {{ invite.game }}
          <v-btn color="success" @click="inviteResponse(invite, true)">Accept</v-btn>
          <v-btn color="error" @click="inviteResponse(invite, false)">Decline</v-btn>
        </template>
      </v-list>
    </v-card>
    <v-card class="invites-sent" v-if="inviteWaiting.inviteId">
      <v-chip v-if="inviteWaiting.waitingFor.length > 0">
        Waiting for response from
        <v-chip v-for="username in inviteWaiting.waitingFor" :key="username">{{ username }}</v-chip>
      </v-chip>
      <v-chip v-if="inviteWaiting.waitingFor.length == 0">
        <input type="text" :value="inviteURL"></input>
      </v-chip>
    </v-card>
  </div>
</template>
<script>
import Socket from "../socket";
export default {
  name: "Invites",
  data() {
    return {
      invites: [],
      inviteWaiting: { waitingFor: [], inviteId: null }
    };
  },
  methods: {
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
      this.invites.push(e);
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
    },
    invite: function(gameType, username) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": ["${username}"] }`
      );
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
    Socket.$off("type:Invite", this.inviteMessage);
    Socket.$off("type:GameStarted", this.gameStartedMessage);
  }
};
</script>
