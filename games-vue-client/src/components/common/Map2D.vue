<template>
  <div class="board-parent">
    <div
      class="board"
      :style="{width: actualWidth*64 + 'px', height: actualHeight*64 + 'px'}"
    >
      <div
        class="pieces pieces-bg"
        :style="{ 'grid-template-columns': `repeat(${actualWidth}, 1fr)`, 'grid-template-rows': `repeat(${actualHeight}, 1fr)` }"
      >
        <template v-for="y in actualHeight">
          <template v-for="x in actualWidth">
            <div
              :key="`${x}_${y}`"
              :class="['piece', 'piece-bg', {actionable: actionable && actionable[`${x-1},${y-1}`]}]"
              @click="onClick({ x: x - 1, y: y - 1 })"
            />
          </template>
        </template>
      </div>
      <div
        class="pieces player-pieces"
        :style="{ 'grid-template-columns': `repeat(${actualWidth}, 1fr)`, 'grid-template-rows': `repeat(${actualHeight}, 1fr)` }"
      >
        <template v-for="piece in gridTiles">
          <div
            :key="piece.key"
            class="grid-element-wrapper"
            :style="{
              gridArea: (piece.y+1) + '/' + (piece.x+1),
              actionable: actionable && actionable[`${piece.x-1},${piece.y-1}`]
            }"
            @click="onClick(piece)"
          >
            <slot :tile="piece" />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "Map2D",
  props: ["width", "height", "grid", "clickHandler", "actionable", "pieceExists"],
  methods: {
    doNothing() {},
    onClick(data) {
      console.log("Map2D Click", data);
      if (this.clickHandler) {
        this.clickHandler(data.x + this.left, data.y + this.top)
      }
    }
  },
  computed: {
    actualGrid() {
      if (!this.grid) return [[]];
      if (this.grid.grid) return this.grid.grid
      return this.grid
    },
    actualWidth() {
      if (!this.grid) return 0;
      if (this.width) return this.width
      if (this.grid.width) return this.grid.width
      return 0
    },
    actualHeight() {
      if (!this.grid) return 0;
      if (this.height) return this.height
      if (this.grid.height) return this.grid.height
      return 0
    },
    top() {
      if (this.grid.top) return this.grid.top
      return 0
    },
    left() {
      if (this.grid.left) return this.grid.left
      return 0
    },
    gridTiles() {
      if (typeof this.actualGrid === 'undefined') {
        return []
      }
      let gridTiles = []
      this.actualGrid.forEach((row, y) => {
        row.forEach((tile, x) => {
          if (this.pieceExists(tile)) {
            gridTiles.push({ key: `tile-${x}-${y}`, x: x, y: y, tile: tile })
          }
        })
      })
      return gridTiles
    }
  }
}
</script>
<!--<style>
@import "../../assets/games-style.css";

</style>-->
<style scoped>
.grid-element-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
</style>