<template>
  <div class="game-dsl-ttt game-piece-color-change">
    <Map2D :width="width" :height="height" :grid="view.board" :clickHandler="onClick" :actionable="actions2.available"
      :pieceExists="e => e.owner !== null">
      <template v-slot:default="slotProps">
        <UrPiece v-if="slotProps.tile.tile.owner !== null"
          :key="slotProps.key"
          :mouseover="doNothing" :mouseleave="doNothing"
          :class="'piece-' + slotProps.tile.tile.owner"
          :onclick="pieceClick"
          :actionable="actions2.available[`${slotProps.tile.x},${slotProps.tile.y}`]"
          :piece="slotProps.tile">
        </UrPiece>
      </template>
    </Map2D>
  </div>
</template>
<script>
import Map2D from "@/components/common/Map2D";
import UrPiece from "../ur/UrPiece";

export default {
  name: "DSLTTT",
  props: ["view", "actions2", "onAction"],
  components: {
    Map2D,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    pieceClick(data) {
      console.log("IGNORED PIECECLICK", data)
    },
    onClick(x, y) {
      if (this.actions2.actionTypes.includes('play')) {
        this.actions2.perform("play", `${x},${y}`);
      } else {
        this.actions2.perform("move", `${x},${y}`);
      }
    }
  },
  computed: {
    width() {
      if (!this.view.board) { return 0 }
      return this.view.board[0].length
    },
    height() {
      if (!this.view.board) { return 0 }
      return this.view.board.length
    }
  }
};
</script>
<style>
@import "../../assets/games-style.css";
</style>
