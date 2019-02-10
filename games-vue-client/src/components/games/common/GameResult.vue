<template>
  <div class="game-over" v-if="gameOverMessage">
    <span class="you" v-if="yourIndex >= 0">YOU</span>
    <span class="you" v-else>{{ winnerName }}</span>
    <span v-if="gameOverMessage.winner" class="win">WIN</span>
    <span v-if="!gameOverMessage.winner" class="loss">LOSE</span>
    <div class="game-over-actions">
      <router-link to="/">Back to Lobby</router-link>
    </div>
  </div>
</template>
<script>
import Socket from "../../../socket";

export default {
  name: "GameResult",
  props: ["yourIndex", "players"],
  data() {
    return {
      winnerName: null,
      gameOverMessage: null
    };
  },
  created() {
    Socket.$on("type:PlayerEliminated", this.messageEliminated);
  },
  beforeDestroy() {
    Socket.$off("type:PlayerEliminated", this.messageEliminated);
  },
  methods: {
    messageEliminated(e) {
      console.log(`Recieved eliminated: ${JSON.stringify(e)}`);
      if (this.yourIndex == e.player) {
        this.gameOverMessage = e;
      }
      if (this.yourIndex < 0 && e.winner) {
        this.gameOverMessage = e;
        this.winnerName = this.players[e.player];
      }
    }
  }
};
</script>
<style scoped>
.game-over {
  margin-top: 20px;
  font-size: 2em;
  font-weight: bolder;
}

.game-over .win {
  color: green;
}
.game-over .loss {
  color: red;
}
</style>
