<template>
  <div class="ur-roll">
    <div class="ur-dice">
      <div v-for="i in 4" class="ur-die">
        <div v-if="roll >= i" class="ur-die-filled"></div>
      </div>
    </div>
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
      this.onDoRoll();
    }
  }
};
</script>
<style scoped>
.ur-roll {
  margin-top: 10px;
}

.ur-dice {
  width: 320px;
  height: 64px;
  margin: auto;
  display: flex;
  justify-content: space-between;
}

.ur-die-filled {
  background: black;
  border-radius: 100%;
  width: 20%;
  height: 20%;
}

.ur-die {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 64px;
  border: 1px solid black;
  border-radius: 12px;
}
</style>
