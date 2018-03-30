<template>
  <div class="ur-roll">
    <div class="ur-dice" @click="onclick()" :class="{ moveable: usable }">
      <div v-for="i in 4" class="ur-die">
        <div v-if="rolls[i - 1]" class="ur-die-filled"></div>
      </div>
    </div>
    <span>{{ roll }}</span>
  </div>

</template>
<script>
function shuffle(array) {
  // https://stackoverflow.com/a/2450976/1310566
  var currentIndex = array.length,
    temporaryValue,
    randomIndex;

  // While there remain elements to shuffle...
  while (0 !== currentIndex) {
    // Pick a remaining element...
    randomIndex = Math.floor(Math.random() * currentIndex);
    currentIndex -= 1;

    // And swap it with the current element.
    temporaryValue = array[currentIndex];
    array[currentIndex] = array[randomIndex];
    array[randomIndex] = temporaryValue;
  }

  return array;
}

export default {
  name: "UrRoll",
  props: ["roll", "usable", "onDoRoll"],
  data() {
    return { rolls: [false, false, false, false] };
  },
  watch: {
    roll: function(newValue, oldValue) {
      console.log("Set roll to " + newValue);
      if (newValue < 0) {
        return;
      }
      this.rolls.fill(false);
      this.rolls.fill(true, 0, newValue);
      console.log(this.rolls);
      shuffle(this.rolls);
      console.log("After shuffle:");
      console.log(this.rolls);
    }
  },
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

.ur-roll span {
  font-size: 2em;
  font-weight: bold;
}

.ur-dice {
  width: 320px;
  height: 64px;
  margin: 5px auto 5px auto;
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
