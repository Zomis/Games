<template>
  <div class="entity">
    <p>E:{{ entity.id }};</p>
    <div v-if="entity.grid" class="grid-parent">
      <div class="grid">
        <Entity v-for="e in entity.grid.flatMap(row => row)" :entity="e" :game="game" :key="e.id"></Entity>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "Entity",
  props: ["game", "entity"],
  data() {
    return {};
  },
  created() {},
  beforeDestroy() {},
  components: {},
  methods: {
    doNothing: function() {},
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `{ "game": "${this.game}", "gameId": "${
          this.gameId
        }", "type": "move", "moveType": "${name}", "move": ${data} }`;
        Socket.send(json);
      }
    },
    messageState(e) {
      console.log(`MessageState: ${e.roll}`);
      if (typeof e.roll !== "undefined") {
        this.ur.doRoll_za3lpa$(e.roll);
        this.rollUpdate(e.roll);
      }
      console.log("AfterState: " + this.ur.toString());
    }
  },
  computed: {}
};
</script>
<style>
@import "../../assets/games-ecs.css";
@import "../../assets/games-uttt-ecs.css";
</style>
