<template>
  <h1>{{ game }} : {{ gameId }} - {{ playerVs }}</h1>
</template>
<script>
export default {
  name: "GameHead",
  props: ["game", "gameId", "players"],
  computed: {
    playerVs: function() {
      if (typeof this.players !== "object") {
        return "local game";
      }
      return this.players[0] + " vs. " + this.players[1];
    }
  }
};
</script>
<style scoped>
</style>
