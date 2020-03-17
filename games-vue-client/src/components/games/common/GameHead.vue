<template>
  <h1>{{ gameDisplayName }} : {{ playerVs }}</h1>
</template>
<script>
import supportedGames from "@/supportedGames"

export default {
  name: "GameHead",
  props: ["gameInfo"],
  computed: {
    gameDisplayName() {
      let game = supportedGames.games[this.gameInfo.gameType]
      return game.displayName ? game.displayName : this.gameType.gameType
    },
    playerVs: function() {
      if (typeof this.gameInfo.players !== "object") {
        return "local game";
      }
      return this.gameInfo.players[0].name + " vs. " + this.gameInfo.players[1].name;
    }
  }
};
</script>
<style scoped>
</style>
