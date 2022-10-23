<template>
  <div>
    <!-- {{ cubes }} -->
    <div class="d-flex justify-space-between" v-if="cubes !== null">
      <!-- <div>
        
      </div> -->
      <img v-for="(cube, cubeIndex) in cubes.cubes" :key="'cube-' + cubeIndex"
        :src="`${path}cube_${cubes.playerIndex}.png`"
        :class="{ grayed: cube.used }" />
    </div>
    <div v-else><!-- empty row --></div>
  </div>
</template>
<script>
export default {
  name: "AlchemistsActionCubesRow",
  props: ["cubes"],
  data() {
    return {
      path: "https://d3ux78k3bc7mem.cloudfront.net/games/alc/"
    };
  }
}
</script>
<style scoped>
.grayed {
  opacity: 0.5;
}
</style>