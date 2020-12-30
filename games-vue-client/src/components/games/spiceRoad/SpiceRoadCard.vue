<template>
  <v-card :class="[actionableClass]" @click="click">
    <v-card-title />
    <v-card-text>
      <div v-if="card.points">
        {{ card.points }}
        <spice-road-resources :caravan="card.cost" />
      </div>
      <div v-if="card.trade">
        <v-row>
          <v-col>
            <spice-road-resources :caravan="card.trade.give" />
            <v-icon color="gray">mdi-bank-transfer-out</v-icon>
            <spice-road-resources :caravan="card.trade.get" />
          </v-col>
        </v-row>
      </div>
      <div v-if="card.upgrade">
        <v-icon v-for="i in card.upgrade" :key="i"
          color="gray"
        >
          mdi-arrow-up-bold-circle
        </v-icon>
      </div>
      <div v-if="card.gain">
        <spice-road-resources :caravan="card.gain" />
      </div>
      <div v-if="card.bonusSpice">
        <v-icon>mdi-plus</v-icon>
        <spice-road-resources :caravan="card.bonusSpice" />
      </div>
    </v-card-text>
  </v-card>
</template>
<script>
import SpiceRoadResources from "./SpiceRoadResources"

export default {
  name: "SpiceRoadCard",
  props: ["card", "actions", "context", "action"],
  components: { SpiceRoadResources },
  methods: {
    click() {
      this.actions.perform('ignored', this.card.id);
    }
  },
  computed: {
    actionableClass() {
      if (!this.actions) return "no-actions";
      if (this.actions.available[this.card.id]) {
        return "actionable"
      }
      return "not-actionable"
    }
  }
}
</script>
<style>
.actionable {
  border-style: solid;
  border-color: cyan !important;
}

:root {
  --spiceRoad-yellow: #ffd166;
  --spiceRoad-red: #ef476f;
  --spiceRoad-green: #06D6A0;
  --spiceRoad-brown: #a5701e;
  --spiceRoad-silver: #b2b9c7;
  --spiceRoad-gold: #eea12c;
}

.coin-gold,
.coin-silver {
  padding: 9px 14px;
  border-style: solid;
  border-width: thin;
  border-color: black !important;
  border-radius: 100%;
}
.coin-gold {
  background-color: var(--spiceRoad-gold) !important;
}
.coin-silver {
  background-color: var(--spiceRoad-silver) !important;
}
</style>