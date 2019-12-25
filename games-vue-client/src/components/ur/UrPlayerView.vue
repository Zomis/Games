<template>
  <div class="player-view">
    <div class="side side-remaining">
      <div class="number">{{ remaining }}</div>
      <div class="pieces-container">
        <div v-for="n in remaining" :key="n" class="piece-small pointer"
          :class="{ ['piece-' + playerIndex]: true, moveable: canPlaceNew && n == remaining }"
          @mouseover="onPlaceNewHighlight(playerIndex)" @mouseleave="mouseleave()"
          :style="{ left: (n-1)*12 + 'px' }" v-on:click="placeNew()">
        </div>
      </div>
    </div>
    <transition name="fade">
      <div class="player-active-indicator" v-if="game.currentPlayer == playerIndex"></div>
    </transition>
    <div class="side side-out">
      <div class="number">{{ out }}</div>
      <div class="pieces-container">
        <div v-for="n in out" :key="n" class="piece-small"
          :class="['piece-' + playerIndex]"
          :style="{ right: (n-1)*12 + 'px' }">
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "UrPlayerView",
  props: [
    "game",
    "playerIndex",
    "onPlaceNew",
    "gamePieces",
    "onPlaceNewHighlight",
    "mouseleave"
  ],
  data() {
    return {};
  },
  methods: {
    placeNew: function() {
      this.onPlaceNew(this.playerIndex);
    }
  },
  computed: {
    remaining: function() {
      return this.gamePieces[this.playerIndex].filter(i => i === 0).length;
    },
    out: function() {
      return this.gamePieces[this.playerIndex].filter(i => i === 15).length;
    },
    canPlaceNew: function() {
      return (
        this.game.currentPlayer == this.playerIndex &&
        this.game.isMoveTime &&
        this.game.canMove_qt1dr2$(this.playerIndex, 0, this.game.roll)
      );
    }
  }
};
</script>
<style scoped>
.player-active-indicator {
  background: black;
  border-radius: 100%;
  width: 20px;
  height: 20px;
}

.number {
  margin: 2px;
  font-weight: bold;
  font-size: 2em;
}

.piece-small {
  position: absolute;
  top: 6px;
  background-size: cover;
  width: 24px;
  height: 24px;
  border: 1px solid black;
}

.pieces-container {
  position: relative;
}
</style>
