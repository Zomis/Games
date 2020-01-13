<template>
  <div class="board-parent">
    <div class="board" :style="{width: width*64 + 'px', height: height*64 + 'px'}">
      <div class="pieces pieces-bg" :style="{ 'grid-template-columns': `repeat(${width}, 1fr)`, 'grid-template-rows': `repeat(${height}, 1fr)` }">
<!--        :class="{ 'moveable': moveableIndex[idx - 1] && movesMade % 2 == gameInfo.yourIndex }" -->
        <div v-for="idx in width*height" :key="idx" class="piece piece-bg"
          @click="onClick({ x: (idx - 1) % width, y: Math.floor((idx - 1) / width) })">
        </div>
      </div>
      <div class="pieces player-pieces" :style="{ 'grid-template-columns': `repeat(${width}, 1fr)`, 'grid-template-rows': `repeat(${height}, 1fr)` }">
        <template v-for="piece in gridTiles">
          <slot :tile="piece" />
        </template>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "Map2D",
  props: ["width", "height", "grid", "clickHandler"],
  methods: {
    doNothing() {},
    onClick(data) {
      if (this.clickHandler) {
        this.clickHandler(data.x, data.y)
      }
    }
  },
  computed: {
    gridTiles() {
      if (typeof this.grid === 'undefined') {
        return []
      }
      let gridTiles = []
      this.grid.forEach((row, y) => {
        row.forEach((tile, x) => {
          if (this.grid[y][x]) {
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
