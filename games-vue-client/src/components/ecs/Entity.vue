<template>
  <div class="entity" @click="click(entity)">
    <p>E:{{ entity.id }}</p>
    <p v-if="entity.owner">O: {{entity.owner}}</p>
    <p v-if="entity.activeBoard">AB: {{entity.activeBoard}}</p>
    <p v-if="entity.currentPlayer">CP: {{entity.currentPlayer}}</p>
    <div v-if="entity.grid" class="grid-parent">
      <div class="grid">
        <Entity v-for="e in entity.grid.flatMap(row => row)" :entity="e"
           v-bind="{game, click}" :key="e.id">
        </Entity>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "Entity",
  props: ["game", "entity", "click"],
  data() {
    return {};
  },
  created() {
    if (typeof this.entity.id === "undefined") {
      console.log("ERROR: " + JSON.stringify(this.entity));
    }
    this.game.entities[this.entity.id] = this.entity;
  },
  beforeDestroy() {},
  components: {},
  methods: {
    messageState(e) {
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
