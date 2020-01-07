<template>
  <div class="game-dsl">
    <Map2D :width="width" :height="height" :grid="view.board" :clickHandler="onClick">
      <template v-slot:default="slotProps">
        <UrPiece
          :key="slotProps.key"
          :mouseover="doNothing" :mouseleave="doNothing"
          class="piece"
          :class="'piece-' + slotProps.tile.tile.owner"
          :onclick="pieceClick"
          :piece="slotProps.tile">
        </UrPiece>
      </template>
    </Map2D>
  </div>
</template>
<script>
import Map2D from "../common/Map2D";
import UrPiece from "../ur/UrPiece";

export default {
  name: "DSLTTT",
  props: ["view", "onAction"],
  components: {
    Map2D,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    pieceClick(data) {
      console.log(`onClick on DSLTTT pieceClick invoked: ${data.x}, ${data.y}`) // This is the one that is used
      this.onAction("play", { x: data.x, y: data.y });
    },
    onClick(x, y) {
      console.log(`onClick on DSLTTT invoked: ${x}, ${y}`)
      this.onAction("play", { x: x, y: y });
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
