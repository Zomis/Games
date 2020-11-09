<template>
  <transition name="fade">
    <div
      class="piece"
      :class="[piece.id, {actionable: actionable}]"
      :style="{ gridArea: (piece.y+1) + '/' + (piece.x+1) }"
      @click="click(piece)"
      @mouseover="mouseover(piece)"
      @mouseleave="mouseleave()"
    />
  </transition>
</template>
<script>
export default {
  name: "UrPiece",
  props: ["piece", "onclick", "mouseover", "mouseleave", "actionable"],
  methods: {
    click: function(piece) {
      this.onclick(piece);
    }
  }
};
</script>
