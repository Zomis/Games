<template>
  <h1>{{ gameInfo.gameType }} : {{ gameInfo.gameId }} - {{ playerVs }}</h1>
</template>
<script>
export default {
  name: "GameHead",
  props: ["gameInfo"],
  computed: {
    playerVs: function() {
      if (typeof this.gameInfo.players !== "object") {
        return "local game";
      }
      return this.gameInfo.players[0] + " vs. " + this.gameInfo.players[1];
    }
  }
};
</script>
<style scoped>
</style>
