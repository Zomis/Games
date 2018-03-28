<template>
  <div class="start-screen">
    <input v-model="name" placeholder="your name" />
    <div class="gametypes">
      <span>Waiting for game: {{ waitingGame }}</span>
      <div v-for="game in games"><button :enabled="!waiting" @click="matchMake(game)">{{ game }}</button></div>
    </div>
    <button @click="requestGameList()">Request game list</button>
    <div class="gamelist">
      <div v-for="game in gameList">
        <button :enabled="!waiting" @click="observe(game)">{{ game }}</button>
      </div>
    </div>

    <button @click="createAI()">Create Bot</button>
  </div>
</template>

<script>
let games = require("../../../games-js/web/games-js");

import Socket from "../socket";
export default {
  name: "StartScreen",
  data() {
    return {
      gameList: [],
      waiting: false,
      waitingGame: null,
      name: "(Unused at the moment)",
      games: ["Connect4", "UR"]
    };
  },
  beforeRouteLeave: function(to, from, next) {
    if (this.name.length === 0) {
      window.alert("You must enter a name.");
      next(false);
    } else {
      next();
    }
  },
  methods: {
    createAI: function() {
      Socket.send("VUEJS");
    },
    observe: function(game) {
      Socket.send(
        `v1:{ "type": "observer", "game": "${game.gameType}", "gameId": "${
          game.gameId
        }", "observer": "start" }`
      );
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
    gameStartedMessage: function(e) {
      this.$router.push(
        `/games/${e.gameType}/${e.gameId}/?playerIndex=${e.yourIndex}`
      );
    }
  },
  created() {
    Socket.$on("type:GameStarted", this.gameStartedMessage);
    Socket.$on("type:GameList", this.gameListMessage);
  },
  beforeDestroy() {
    Socket.$off("type:GameStarted", this.gameStartedMessage);
    Socket.$off("type:GameList", this.gameListMessage);
  }
};
</script>

<style scoped>
h1, h2 {
  font-weight: normal;
}
</style>
