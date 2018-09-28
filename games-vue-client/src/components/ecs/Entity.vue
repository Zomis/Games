<template>
  <div :data-entity="entity.id" class="entity" @click="click(entity)" :class="{['owner-' + entity.owner]: entity.owner !== null, owned: entity.owner !== null}">
    <span v-if="entity.activeBoard">AB: {{entity.activeBoard}}</span>
    <span v-if="entity.currentPlayer > -1">CP: {{entity.currentPlayer}}</span>
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
