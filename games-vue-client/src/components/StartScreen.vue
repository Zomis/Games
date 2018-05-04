<template>
  <div class="start-screen">
    <h1 class="login-name">Welcome, {{ loginName }}</h1>

    <v-card v-for="(users, gameType) in availableUsers" :key="gameType" class="games">
      <v-toolbar color="cyan" dark>
        <v-toolbar-title>{{ gameType }}</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn round :disabled="waiting" @click="matchMake(gameType)">Play anyone</v-btn>
      </v-toolbar>
      <v-list two-line>
        <template v-for="name in users">
          <v-btn class="username" :class="'user-' + gameType" @click="invite(gameType, name)">{{ name }}</v-btn>
        </template>
      </v-list>
    </v-card>

    <v-card class="invites-sent" v-if="inviteWaiting.inviteId">
      Waiting for response from
      <v-chip v-for="username in inviteWaiting.waitingFor" :key="username">{{ username }}</v-chip>
    </v-card>

    <v-card class="invites">
      <v-list two-line>
        <template v-for="invite in invites">
          {{ invite.host }} invites you to play {{ invite.game }}
          <v-btn color="success" @click="inviteResponse(invite, true)">Accept</v-btn>
          <v-btn color="error" @click="inviteResponse(invite, false)">Decline</v-btn>
        </template>
      </v-list>
    </v-card>

    <div class="gamelist">
      <button @click="requestGameList()">Request game list</button>
      <div v-for="game in gameList">
        <button :enabled="!waiting" @click="observe(game)">{{ game }}</button>
      </div>
    </div>

    <p>If you want to play Royal game of UR against an AI, click "UR" and then click "Create Bot"</p>
    <p>You can observe existing games by clicking "Request game list" and then click on the game you want to observe.</p>
  </div>
</template>

<script>
import Socket from "../socket";
export default {
  name: "StartScreen",
  data() {
    return {
      invites: [],
      inviteWaiting: { waitingFor: [], inviteId: null },
      availableUsers: {},
      gameList: [],
      waiting: false,
      waitingGame: null
    };
  },
  methods: {
    observe: function(game) {
      this.$router.push(
        `/games/${game.gameType}/${game.gameId}/?playerIndex=-42`
      );
    },
    requestGameList: function() {
      Socket.send(`v1:{ "type": "GameList" }`);
    },
    gameListMessage: function(message) {
      this.gameList = message.list;
    },
    matchMake: function(game) {
      this.waiting = true;
      this.waitingGame = game;
      Socket.send(`v1:{ "game": "${game}", "type": "matchMake" }`);
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
    lobbyMessage: function(e) {
      this.availableUsers = e.users;
    },
    gameStartedMessage: function(e) {
      let games = {
        UR: "RoyalGameOfUR",
        UTTT: "UTTT",
        Connect4: "Connect4"
      };
      this.$router.push({
        name: games[e.gameType],
        params: {
          players: e.players,
          gameId: e.gameId,
          playerIndex: e.yourIndex
        }
      });
    }
  },
  created() {
    //    {"type":"Lobby","users":{"UR":["guest-44522"],"Connect4":["guest-44522"]}}
    Socket.$on("type:InviteWaiting", this.inviteWaitingMessage);
    Socket.$on("type:Invite", this.inviteMessage);
    Socket.$on("type:Lobby", this.lobbyMessage);
    Socket.$on("type:GameStarted", this.gameStartedMessage);
    Socket.$on("type:GameList", this.gameListMessage);
    Socket.send(
      `{ "type": "ClientGames", "gameTypes": ["UR", "Connect4", "UTTT"], "maxGames": 1 }`
    );
    Socket.send(`{ "type": "ListRequest" }`);
  },
  computed: {
    loginName() {
      return Socket.loginName;
    }
  },
  beforeDestroy() {
    Socket.$off("type:InviteWaiting", this.inviteWaitingMessage);
    Socket.$off("type:Invite", this.inviteMessage);
    Socket.$off("type:Lobby", this.lobbyMessage);
    Socket.$off("type:GameStarted", this.gameStartedMessage);
    Socket.$off("type:GameList", this.gameListMessage);
  }
};
</script>

<style scoped>
h1,
h2 {
  font-weight: normal;
}

.gametypes {
  margin-top: 24px;
  margin-bottom: 24px;
}

.games {
  margin: 32px;
  width: 25%;
  margin: 32px auto 32px auto;
}
</style>
