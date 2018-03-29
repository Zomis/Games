<template>
  <div class="player-view">
    <div class="pieces-remaining">{{ remaining }}</div>
    <div class="pieces-out">{{ out }}</div>
  </div>
</template>
<script>
export default {
  name: "UrPlayerView",
  props: ["game", "playerIndex"],
  data() {
    return {};
  },
  computed: {
    remaining: function() {
      return this.game.piecesCopy[this.playerIndex].filter(i => i === 0).length;
    },
    out: function() {
      return this.game.piecesCopy[this.playerIndex].filter(i => i === 15)
        .length;
    }
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
