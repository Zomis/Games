<template>
  <div class="piece"
    v-on:click="click(piece)"
    :class="piece.id"
     v-bind:style="{ gridArea: (piece.y+1) + '/' + (piece.x+1) }">
  </div>
</template>
<script>
export default {
  name: "UrPiece",
  props: ["piece", "onclick"],
  methods: {
    click: function(piece) {
      console.log(piece);
      this.onclick(piece);
    }
  }
};
</script>
