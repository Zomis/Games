<template>
  <transition name="fade">
  <div class="piece"
    v-on:click="click(piece)"
    :class="piece.id"
    @mouseover="mouseover(piece)" @mouseleave="mouseleave()"
     v-bind:style="{ gridArea: (piece.y+1) + '/' + (piece.x+1) }">
  </div>
  </transition>
</template>
<script>
export default {
  name: "UrPiece",
  props: ["piece", "onclick", "mouseover", "mouseleave"],
  methods: {
    click: function(piece) {
      this.onclick(piece);
    }
  }
};
</script>
