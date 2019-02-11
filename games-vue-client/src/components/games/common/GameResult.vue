<template>
  <div class="game-over" v-if="gameOverMessage">
    <span class="you" v-if="gameInfo.yourIndex >= 0">YOU</span>
    <span class="you" v-else>{{ winnerName }}</span>
    <span v-if="gameOverMessage.winner" class="win">WIN</span>
    <span v-if="!gameOverMessage.winner" class="loss">LOSE</span>
    <div class="game-over-actions">
      <router-link to="/">Back to Lobby</router-link>
      <v-btn color="info" @click="playAgain()">Play again</v-btn>
    </div>
    <Invites />
  </div>
</template>
<script>
import Socket from "../../../socket";
import Invites from "../../Invites";

export default {
  name: "GameResult",
  props: ["gameInfo"],
  components: { Invites },
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
    playAgain() {
      let opponent = this.gameInfo.players[1 - this.gameInfo.yourIndex];
      Socket.send(
        `{ "type": "Invite", "gameType": "${
          this.gameInfo.gameType
        }", "invite": ["${opponent}"] }`
      );
    },
    messageEliminated(e) {
      if (
        this.gameInfo.gameType !== e.gameType ||
        this.gameInfo.gameId !== e.gameId
      ) {
        return;
      }
      console.log(`Recieved eliminated: ${JSON.stringify(e)}`);
      if (this.gameInfo.yourIndex == e.player) {
        this.gameOverMessage = e;
      }
      if (this.gameInfo.yourIndex < 0 && e.winner) {
        this.gameOverMessage = e;
        this.winnerName = this.gameInfo.players[e.player];
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
