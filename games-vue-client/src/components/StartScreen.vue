<template>
  <div class="start-screen">
    <h1 class="login-name">Welcome, {{ loginName }}</h1>

    <v-card v-for="(users, gameType) in availableUsers" :key="gameType" class="games">
      <v-toolbar color="cyan" dark>
        <v-toolbar-title>{{ gameType }}</v-toolbar-title>
        <v-spacer></v-spacer>
        <v-btn round :disabled="waiting" @click="matchMake(gameType)">Play anyone</v-btn>
        <v-btn round :disabled="waiting" @click="inviteLink(gameType)">Invite with link</v-btn>
      </v-toolbar>
      <v-list two-line>
        <template v-for="name in users">
          <v-btn class="username" :class="'user-' + gameType" @click="invite(gameType, name)">{{ name }}</v-btn>
        </template>
      </v-list>
    </v-card>

    <Invites />

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
import Invites from "./Invites";
export default {
  name: "StartScreen",
  data() {
    return {
      availableUsers: {},
      gameList: [],
      waiting: false,
      waitingGame: null
    };
  },
  components: {
    Invites
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
    invite: function(gameType, username) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": ["${username}"] }`
      );
    },
    inviteLink(gameType, username) {
      Socket.send(
        `{ "type": "Invite", "gameType": "${gameType}", "invite": [] }`
      );
    },
    lobbyMessage: function(e) {
      this.availableUsers = e.users;
    }
  },
  created() {
    //    {"type":"Lobby","users":{"UR":["guest-44522"],"Connect4":["guest-44522"]}}
    Socket.$on("type:Lobby", this.lobbyMessage);
    Socket.$on("type:GameList", this.gameListMessage);
    Socket.send(
      `{ "type": "ClientGames", "gameTypes": ["UR", "Connect4", "UTTT", "UTTT-ECS"], "maxGames": 1 }`
    );
    Socket.send(`{ "type": "ListRequest" }`);
  },
  computed: {
    loginName() {
      return Socket.loginName;
    }
  },
  beforeDestroy() {
    Socket.$off("type:Lobby", this.lobbyMessage);
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
