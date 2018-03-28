<template>
  <div class="piece piece-flower"
     v-bind:style="{ transform: 'translate(' + x * 64 + 'px, ' + y * 64 + 'px)' }">
  </div>
</template>
<script>
export default {
  name: "UrFlower",
  props: ["x", "y"]
};
</script>
