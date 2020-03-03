<template>
  <div class="game-dsl-ttt game-piece-color-change">
    <Map2D :width="width" :height="height" :grid="view.board" :clickHandler="onClick" :actionable="{ ...actions.play, ...actions.move }"
      :pieceExists="e => e.owner !== null">
      <template v-slot:default="slotProps">
        <UrPiece v-if="slotProps.tile.tile.owner !== null"
          :key="slotProps.key"
          :mouseover="doNothing" :mouseleave="doNothing"
          :class="'piece-' + slotProps.tile.tile.owner"
          :onclick="pieceClick"
          :actionable="actions.play && actions.play[`${slotProps.tile.x},${slotProps.tile.y}`] || actions.move && actions.move[`${slotProps.tile.x},${slotProps.tile.y}`]"
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
  props: ["view", "actions", "onAction"],
  components: {
    Map2D,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    pieceClick(data) {
//      console.log(`onClick on DSLTTT pieceClick invoked: ${data.x}, ${data.y}`) // This is the one that is used
//      this.onClick(data.x, data.y)
      console.log("IGNORED PIECECLICK", data)
    },
    onClick(x, y) {
      console.log(`onClick on DSLTTT invoked: ${x}, ${y}`)
      if (this.actions.play) {
        this.onAction("play", `${x},${y}`);
      } else {
        this.onAction("move", `${x},${y}`);
      }
      console.log("AFTER ONCLICK", x, y)
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
