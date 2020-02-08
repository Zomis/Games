<template>
  <div class="board-parent">
    <div class="board" :style="{width: width*64 + 'px', height: height*64 + 'px'}">
      <div class="pieces pieces-bg" :style="{ 'grid-template-columns': `repeat(${width}, 1fr)`, 'grid-template-rows': `repeat(${height}, 1fr)` }">
        <template v-for="y in height">
          <template v-for="x in width">
            <div :key="`${x}_${y}`"
              :class="['piece', 'piece-bg', {actionable: actionable && actionable[`${x-1},${y-1}`]}]"
              @click="onClick({ x: x - 1, y: y - 1 })">
            </div>
          </template>
        </template>
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
  props: ["width", "height", "grid", "clickHandler", "actionable"],
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
