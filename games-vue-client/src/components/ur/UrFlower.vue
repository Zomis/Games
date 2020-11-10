<template>
  <div
    class="piece piece-flower"
    :style="{ 'grid-area': (y+1) + '/' + (x+1) }"
  />
</template>
<script>
export default {
  name: "UrFlower",
  props: ["x", "y"]
};
</script>
