<template>
  <v-container fluid>
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
      <v-col>
        {{ view.deck }} remaining in deck
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <CardZone class="board">
          <SetCard class="list-complete-item animate" v-for="card in cards" :key="card.key" :card="card" :onClick="cardClick" />
        </CardZone>
      </v-col>
    </v-row>
    <v-row>
      <v-col v-for="(player, playerIndex) in view.players" :key="playerIndex">
        <v-card>
          <v-card-title>
            <PlayerProfile show-name :player="context.players[playerIndex]" />
          </v-card-title>
          <v-card-text>
            <v-row>
              {{ player.points }} points
            </v-row>
            <v-row v-if="player.lastResult">
              <v-col>
                <div v-for="(property, propertyName) in player.lastResult.properties" :key="propertyName">
                  <v-icon color="green" v-if="property.valid">mdi-check-circle</v-icon>
                  <v-icon color="red" v-else>mdi-close-circle</v-icon>
                  <span v-if="property.uniqueCount == 1">All {{ propertyName }}s equal</span>
                  <span v-if="property.uniqueCount == 2">The {{ propertyName }}s does not match: Two {{ property.majorityValue }} and one {{ property.minorityValue }}</span>
                  <span v-if="property.uniqueCount == 3">All {{ propertyName }}s unique</span>
                </div>
              </v-col>
            </v-row>
            <v-row v-if="player.lastResult">
              <v-col v-if="player.lastResult.valid">
                <v-icon color="green">mdi-check-circle</v-icon>
                <span>Set Found!</span>
              </v-col>
              <v-col v-else>
                <v-icon color="red">mdi-close-circle</v-icon>
                <span>Not a set</span>
              </v-col>
            </v-row>
            <v-row v-if="player.lastResult">
              <v-col>
                <SetCard class="list-complete-item animate" v-for="card in player.lastResult.cards" :key="card.key" :card="card" />
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import CardZone from "@/components/games/common/CardZone"
import SetCard from "./SetCard"

export default {
  name: "Set",
  props: ["view", "actions", "context"],
  components: {
      PlayerProfile,
      CardZone,
      SetCard
  },
  methods: {
    cardClick(card) {
      this.actions.perform('ignored', "set-" + card.key);
    }
  },
  computed: {
    cards() {
      if (!this.view.cards) return []
      return this.view.cards
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
