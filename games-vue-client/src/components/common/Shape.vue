<template>
  <div
    class="shape"
    :class="{
      ['shape-' + type]: true, ['color-' + color]: true,
      'highlight': highlight
    }"
    :style="{ width: size + 'px', height: size + 'px' }"
    @click="onClick"
  />
</template>
<script>
export default {
  name: "Shape",
  props: ["type", "size", "color", "onClick", "highlight"],
  methods: {}
}
</script>
<style scoped>
div.shape {
    display: inline-block;
}
div.shape.color-blue {
    background-color: blue;
}
div.shape.color-red {
    background-color: red;
}
div.shape.highlight {
    border: 3px solid cyan;
}
</style>
