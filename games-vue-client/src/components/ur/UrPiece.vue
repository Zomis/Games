<template>
  <div class="piece"
  :class="id"
     v-bind:style="{ transform: 'translate(' + x * 64 + 'px, ' + y * 64 + 'px)' }">
  </div>
</template>
<script>
export default {
  name: "UrPiece",
  props: ["x", "y", "id"]
};
</script>
