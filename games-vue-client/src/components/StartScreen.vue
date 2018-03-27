<template>
  <div class="start-screen">
    <input v-model="name" placeholder="your name" />
    <ul class="gamelist">
      <div v-for="game in games"><button :enabled="!waiting" @click="matchMake(game)">{{ game }}</button></div>
    </ul>
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
  },
  beforeDestroy() {
    Socket.$off("type:GameStarted", this.gameStartedMessage);
  }
};
</script>

<style scoped>
h1, h2 {
  font-weight: normal;
}
</style>
