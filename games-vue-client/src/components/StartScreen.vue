<template>
  <div class="start-screen">
    <input v-model="name" placeholder="your name" />
    <ul class="gamelist">
      <li v-for="game in games"><button :enabled="!waiting" @click="matchMake(game)">{{ game }}</button></li>
    </ul>
  </div>
</template>

<script>
let games = require("../../../games-js/web/games-js");
let ur = new games.net.zomis.games.ur.RoyalGameOfUr_init();
console.log(ur.toString());

for (var i = 0; i < 40; i++) {
  ur.doRoll();
  if (ur.isMoveTime) {
    var player = ur.currentPlayer;
    var roll = ur.roll;
    for (var m = 0; m < 15; m++) {
      if (ur.move_qt1dr2$(ur.currentPlayer, m, roll)) {
        console.log(player + " made move " + m + " for roll " + roll);
        break;
      }
    }
  }
}
console.log(ur.toString());

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
  created: function() {
    // Socket.send({ }) // Request game list
  },
  methods: {
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
