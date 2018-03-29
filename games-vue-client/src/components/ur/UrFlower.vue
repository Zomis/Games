<template>
  <div class="piece piece-flower"
     v-bind:style="{ 'grid-area': (y+1) + '/' + (x+1) }">
  </div>
</template>
<script>
export default {
  name: "UrFlower",
  props: ["x", "y"]
};
</script>
