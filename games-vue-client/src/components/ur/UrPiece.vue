<template>
  <div class="piece"
     v-bind:style="{ transform: 'translate(' + x * 64 + 'px, ' + y * 64 + 'px)' }">
  </div>
</template>
<script>
export default {
  name: "UrPiece",
  props: ["x", "y"]
};
</script>
