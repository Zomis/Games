<template>
  <div class="player-view">
    <div class="pieces-remaining">{{ game.piecesCopy[playerIndex].filter(i => i === 0).length }}</div>
    <div class="pieces-out">{{ game.piecesCopy[playerIndex].filter(i => i === 15).length }}</div>
  </div>
</template>
<script>
export default {
  name: "UrPlayerView",
  props: ["game", "playerIndex"],
  data() {
    return {};
  }
};
</script>
<style scoped>
.pieces-remaining {
  float: left;
}

.pieces-out {
  float: right
}
</style>
