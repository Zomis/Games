<template>
  <div class="player-view">
    <div class="side side-remaining">
      <div class="number">{{ remaining }}</div>
      <div class="pieces-container">
        <div v-for="n in remaining" :key="n" class="piece-small pointer"
          :class="{ ['piece-' + playerIndex]: true, moveable: canPlaceNew && n === remaining }"
          @mouseover="onPlaceNewHighlight(playerIndex)" @mouseleave="mouseleave()"
          :style="{ left: (n-1)*12 + 'px' }" v-on:click="placeNew()">
        </div>
      </div>
    </div>
    <transition name="fade">
      <div class="player-active-indicator" v-if="view.currentPlayer == playerIndex"></div>
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
    "view",
    "playerIndex",
    "onPlaceNew",
    "onPlaceNewHighlight",
    "mouseleave"
  ],
  methods: {
    placeNew() {
      this.onPlaceNew(this.playerIndex);
    }
  },
  computed: {
    isMoveTime() {
      return this.view.roll > 0
    },
    remaining() {
      return this.view.pieces[this.playerIndex].filter(i => i === 0).length;
    },
    out() {
      return this.view.pieces[this.playerIndex].filter(i => i === 15).length;
    },
    canPlaceNew() {
      return (
        this.view.currentPlayer == this.playerIndex &&
        this.isMoveTime &&
        this.view.pieces[this.playerIndex].every(i => i !== this.view.roll)
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
