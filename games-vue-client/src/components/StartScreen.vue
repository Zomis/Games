<template>
  <div class="start-screen">
    <div class="login-name">Welcome, {{ loginName }}</div>

    <div v-for="(users, key) in availableUsers" class="games">
      <div class="gametitle">{{ key }}</div>
      <button class="username" :class="'user-' + key" v-for="name in users" @click="invite(key, name)">{{ name }}</button>
    </div>

    <div class="gametypes">
      <span>Waiting for game: {{ waitingGame }}</span>
      <div v-for="game in games">
        <button :enabled="!waiting" @click="matchMake(game)">{{ game }}</button>
      </div>
      <button @click="createAI()">Create UR Bot</button>
    </div>
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
      availableUsers: {},
      gameList: [],
      waiting: false,
      waitingGame: null,
      games: ["Connect4", "UR"]
    };
  },
  methods: {
    createAI: function() {
      Socket.send("VUEJS");
    },
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
    lobbyMessage: function(e) {
      this.availableUsers = e.users;
    },
    gameStartedMessage: function(e) {
      this.$router.push({
        name: "RoyalGameOfUR",
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
    Socket.$on("type:Lobby", this.lobbyMessage);
    Socket.$on("type:GameStarted", this.gameStartedMessage);
    Socket.$on("type:GameList", this.gameListMessage);
    Socket.send(
      `{ "type": "ClientGames", "gameTypes": ["UR", "Connect4"], "maxGames": 1 }`
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
</style>
