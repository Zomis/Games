<template>
  <div class="tv">
    <component v-if="game !== null" :is="game.component" :gameInfo="currentGameInfo"></component>
  </div>
</template>
<script>
import Socket from "../socket";

export default {
  name: "TVScreen",
  props: [],
  data() {
    return {
      currentGameInfo: {
        gameType: "",
        gameId: "",
        yourIndex: -40,
        players: []
      }
    };
  },
  created() {
    Socket.$on("type:TVGame", this.tvGame);
    Socket.send(`{ "type": "tv", "action": "start" }`);
  },
  beforeDestroy() {
    Socket.$off("type:TVGame", this.tvGame);
    Socket.send(`{ "type": "tv", "action": "stop" }`);
  },
  methods: {
    tvGame(data) {
      this.$store.dispatch("observe", data);
      this.currentGameInfo = data;
    }
  },
  computed: {
    game() {
      let gameType = this.$store.state[this.currentGameInfo.gameType];
      if (!gameType) {
        return null;
      }
      let game = gameType.games[this.currentGameInfo.gameId];
      if (!game) {
        return null;
      }
      return game;
    }
  }
};
</script>
