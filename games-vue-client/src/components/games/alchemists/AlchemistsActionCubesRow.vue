<template>
  <div :class="{ choosable: actionable[actionSpace.name] }" @click="click">
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
  props: ["cubes", 'actionable', 'row', 'actionSpace', 'onClick'],
  methods: {
    click() {
      this.onClick(this.actionSpace.name);
    },
  },
  data() {
    return {
      path: "https://d3ux78k3bc7mem.cloudfront.net/games/alc/"
    };
  }
}
</script>
<style scoped>
.choosable {
  border: 2px solid #0f0;
}

.grayed {
  opacity: 0.5;
}
</style>