<template>
  <v-container fluid>
    <v-row>
      <v-col align-self="start">
        {{ view }}
        <v-card class="d-flex justify-space-between">
          <v-card v-for="(player, index) in view.players" :key="index" class="flex-grow-1">
            <v-card-title>
              <player-profile :playerIndex="index" :context="context" showName />
            </v-card-title>
            <v-card-text>
              {{ player }}
            </v-card-text>
          </v-card>
        </v-card>
        <v-card>
          <v-row>
            <v-card class="card">
              Morale
              <p class="label">{{ view.morale }}</p>
            </v-card>
            <v-card class="card">
              Trials
              <p class="label">{{ view.trials }}</p>
            </v-card>
            <v-card>
              <p v-for="card in view.playedCards" :key="card">
                {{ card }}
              </p>
            </v-card>
          </v-row>
        </v-card>
        <v-card>
          <v-row>
            <v-card>
              {{ currentPlayer }}
            </v-card>
          </v-row>
          <v-row>
          </v-row>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import PlayerProfile from '../common/PlayerProfile.vue';
export default {
  components: { PlayerProfile },
  name: "Grizzled",
  props: ["view", "actions", "context"],
  methods: {
  },
  watch: {
  },
  data() {
    return {
      path: "https://d3ux78k3bc7mem.cloudfront.net/games/grizzled",
    }
  },
  computed: {
    currentPlayer() {
      if (!this.view.players) return {};
      return this.view.players[this.context.viewer]
    }
  }
};
</script>
<style scoped>
.card {
  width: 92px;
  height: 142px;
}

.label {
  font-size: 30pt;
  border-radius: 10px;
  color: black;
}
</style>
