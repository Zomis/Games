<template>
  <div class="ur-roll">
    <span>{{ roll }}</span>
    <button :disabled="!usable" @click="onclick()">Roll</button>
  </div>

</template>
<script>
export default {
  name: "UrRoll",
  props: ["roll", "usable", "onDoRoll"],
  methods: {
    onclick: function() {
      console.log("UrRoll onclick");
      this.onDoRoll();
    }
  }
};
</script>
<style scoped>
.ur-roll {
  margin-top: 10px;

}
</style>
