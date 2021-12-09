<template>
  <div
    class="tile"
    :class="tileClass"
  >
    <v-icon
      v-for="i in tile.crowns"
      :key="i"
      color="orange"
    >
      mdi-crown
    </v-icon>
    <v-icon v-if="tile.crowns === 0">
      mdi-pen
    </v-icon>
  </div>
</template>
<script>
export default {
  name: "KingDominoTile",
  props: ["tile"],
  computed: {
    tileClass() {
      if (this.tile.tile === null) return "tile-unused"
      if (this.tile.tile === "") return "tile-empty"
      return 'tile-' + this.tile.tile.toLowerCase()
    }
  }
}
</script>
<style scoped>
.tile {
    display: inline-block;
}

.tile-castle {
    background-color: purple;
}
.tile-field {
    background-color: lime;
}
.tile-forest {
    background-color: green;
}
.tile-water {
    background-color: cyan;
}
.tile-mine {
    background-color: black;
}
.tile-sand {
    background-color: yellow;
}
.tile-dirt {
    background-color: brown;
}
.tile-empty {
    background-color: grey;
}


</style>