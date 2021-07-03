<template>
  <div class="game-dsl-ttt game-piece-color-change">
    <GameHead
      v-if="context"
      :context="context"
    />
    <Map2D
      :width="width"
      :height="height"
      :grid="view.board"
      :click-handler="onClick"
      :actionable="actions.available"
      :piece-exists="e => e.owner !== null"
    >
      <template v-slot:default="slotProps">
        <UrPiece
          v-if="slotProps.tile.tile.owner !== null"
          :key="slotProps.key"
          :mouseover="doNothing"
          :mouseleave="doNothing"
          :class="'piece-' + slotProps.tile.tile.owner"
          :onclick="pieceClick"
          :actionable="actions.available[`${slotProps.tile.x},${slotProps.tile.y}`]"
          :piece="slotProps.tile"
        />
      </template>
    </Map2D>
  </div>
</template>
<script>
import Map2D from "@/components/common/Map2D";
import UrPiece from "../ur/UrPiece";
import GameHead from "@/components/games/common/GameHead";

export default {
  name: "DSLTTT",
  props: ["view", "actions", "onAction", "context"],
  components: {
    GameHead,
    Map2D,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    pieceClick(data) {
      console.log("IGNORED PIECECLICK", data)
    },
    onClick(x, y) {
      let actionName = this.view.actionName
      this.actions.perform(actionName, `${x},${y}`);
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
