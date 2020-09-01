<template>
  <v-container fluid>
    <GameTreeView :view="view" />
    <GameTreeView :actions="actions" />

    <svg width="0" height="0">
      <!--  Define the patterns for the different fill colors  -->
      <pattern id="striped-red" patternUnits="userSpaceOnUse" width="4" height="4">
        <path d="M-1,1 H5" style="stroke:#e74c3c; stroke-width:1" />
      </pattern>
      <pattern id="striped-green" patternUnits="userSpaceOnUse" width="4" height="4">
        <path d="M-1,1 H5" style="stroke:#27ae60; stroke-width:1" />
      </pattern>
      <pattern id="striped-purple" patternUnits="userSpaceOnUse" width="4" height="4">
        <path d="M-1,1 H5" style="stroke:#8e44ad; stroke-width:1" />
      </pattern>
    </svg>

    <v-row>
      <CardZone class="board">
        <SetCard class="list-complete-item animate" v-for="card in cards" :key="card.key" :card="card" />
      </CardZone>
    </v-row>
  </v-container>
</template>

<script>
import GameTreeView from "@/components/games/debug/GameTreeView"
import CardZone from "@/components/games/common/CardZone"
import SetCard from "./SetCard"

export default {
  name: "Set",
  props: ["view", "actions", "players"],
  components: {
      GameTreeView,
      CardZone,
      SetCard
  },
  computed: {
    cards() {
      if (!this.view.cards) return []

      return this.view.cards.map((card) => {
        const cardProps = card.split('-')

        return {
          key: card,
          count: cardProps[0],
          shape: cardProps[1],
          filling: cardProps[2],
          color: cardProps[3]
        }
      })
    }
  }
};
</script>
<style scoped>
@import "../../../assets/games-style.css";

.board {
  display: flex;
  flex: 1;
  flex-flow: row wrap;
  background: #eee;
  padding: 20px;
  max-width: 80vh;
  margin: 0 20px;
  border: 0;
}

.color-RED {
 background-color: #ef476f !important;
}
.color-PURPLE {
  background-color: #7057ff !important;
}
.color-GREEN {
 background-color: #06D6A0 !important;
}
</style>
