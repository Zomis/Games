<template>
  <v-container fluid>
    <v-row>
      <v-col align-self="start">
        <v-row class="d-flex justify-space-between">
          <v-card v-for="(player, index) in view.players" :key="index" class="flex-grow-1">
            <v-card-title>
              <player-profile :playerIndex="index" :context="context" showName />
            </v-card-title>
            <v-card-text>
              {{ player.placedSupportTile }}
              <v-icon v-if="player.placedSupportTile">{{ supports[player.placedSupportTile] }}</v-icon>
              <p>
                {{ player.character.name }}
                <v-icon :color="player.charmAvailable ? 'green' : 'gray'">mdi-clover</v-icon>
                <v-icon>{{ threats[player.character.luckyCharm] }}</v-icon>
              </p>

              <v-row>
                <v-icon v-if="index == view.missionLeaderIndex" color="orange">mdi-star</v-icon>
                <v-icon v-if="index == view.currentPlayerIndex" color="red">mdi-bell-ring</v-icon>
                <v-icon v-if="player.withdrawn">mdi-coffee</v-icon>
                <v-icon v-for="i in player.speechesAvailable" :key="i">mdi-chat</v-icon>
                <v-icon v-if="player.withdrawn">mdi-coffee</v-icon>

                <v-icon>mdi-hand-back-right</v-icon>
                <p v-if="!player.hand.length">{{ player.hand }}</p>
                <p v-else>{{ player.hand.length }}</p>

                <v-icon>mdi-coffee</v-icon><p>{{ player.supportTiles }}</p>
              </v-row>
              
              <v-row class="d-flex">
                <GrizzledCard v-for="hardKnock in player.hardKnocks" :key="hardKnock.name" :card="hardKnock" :threats="threats" />
              </v-row>
            </v-card-text>
          </v-card>
        </v-row>
        <v-row>
          <v-card>
            <p>Round {{ view.round }}</p>
            {{ view.activeThreats }}

          </v-card>
        </v-row>
        <v-row>
          <v-card class="card">
            <v-card-text>
            </v-card-text>
            Morale
            <p class="label">{{ view.morale }}</p>
          </v-card>
          <v-card class="card">
            <v-card-text>
            </v-card-text>
            Trials
            <p class="label">{{ view.trials }}</p>
          </v-card>
          <v-card>
            {{ view.speeches }}

            <GrizzledCard v-for="card in view.playedCards" :key="card.id" :card="card" :threats="threats" />
          </v-card>
        </v-row>
        <v-card>
          <v-row v-if="currentPlayer">
            <v-card>
              <v-card-title>
                <player-profile :playerIndex="context.viewer" :context="context" showName />
              </v-card-title>
              <v-card-text>
                {{ view.actions }}
                <p>
                  {{ currentPlayer.character.name }}
                  <v-icon :color="currentPlayer.charmAvailable ? 'green' : 'gray'">mdi-clover</v-icon>
                  <v-icon>{{ threats[currentPlayer.character.luckyCharm] }}</v-icon>
                </p>

                <v-icon v-if="currentPlayerIndex == view.missionLeaderIndex" color="orange">mdi-star</v-icon>
                <v-icon v-if="currentPlayerIndex == view.currentPlayerIndex" color="red">mdi-bell-ring</v-icon>
                <v-icon v-if="currentPlayer.withdrawn">mdi-coffee</v-icon>
                <v-icon v-for="i in currentPlayer.speechesAvailable" :key="i">mdi-chat</v-icon>
                <v-icon v-if="currentPlayer.withdrawn">mdi-coffee</v-icon>

                <v-icon>mdi-hand-back-right</v-icon>
                <p v-if="!currentPlayer.hand.length">{{ currentPlayer.hand }}</p>
                <p v-else>{{ currentPlayer.hand.length }}</p>

                <v-icon>mdi-coffee</v-icon><p>{{ currentPlayer.supportTiles }}</p>

                <v-row>
                  <GrizzledCard v-for="card in currentPlayer.hand" :key="card.id" :card="card" :threats="threats" />
                </v-row>

                <div v-for="(action, actionName) in view.actions.actions" :key="actionName">
                  {{ actionName }}
                  {{ action }}
                  <v-btn v-for="(parameter, index) in action" :key="index" @click="click(actionName, parameter)">{{ parameter }}</v-btn>
                </div>
                <v-btn @click="performChosen">Perform chosen</v-btn>
              </v-card-text>
            </v-card>
          </v-row>
          <v-row>
          </v-row>
        </v-card>
        <v-card>
          <v-row>
            {{ view.discarded }}
          </v-row>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import PlayerProfile from '../common/PlayerProfile.vue';
import GrizzledCard from './GrizzledCard';
export default {
  components: { PlayerProfile, GrizzledCard },
  name: "Grizzled",
  props: ["view", "actions", "context"],
  methods: {
    click(actionName, parameter) {
      if (parameter.id || parameter.id === 0) this.actions.choose(actionName, parameter.id);
      else this.actions.choose(actionName, parameter);
    },
    performChosen() {
      this.actions.performChosen()
    }
  },
  watch: {
  },
  data() {
    return {
      path: "https://d3ux78k3bc7mem.cloudfront.net/games/grizzled",
      supports: {
        "Left": "mdi-skip-previous",
        "Right": "mdi-skip-next",
        "DoubleLeft": "mdi-backward",
        "DoubleRight": "mdi-forward",
      },
      threats: {
        "Trap": "mdi-alert-decagram",
        "HardKnock": "mdi-lightning-bolt",
        "Rain": "mdi-weather-pouring",
        "Snow": "mdi-snowflake",
        "Night": "mdi-weather-night",
        "Shell": "mdi-ammunition",
        "Mask": "mdi-drama-masks",
        "Whistle": "mdi-whistle",
      }
    }
  },
  /*
Support:
<v-icon>mdi-skip-forward</v-icon>
<v-icon>mdi-skip-backward</v-icon>
<v-icon>mdi-skip-next</v-icon>
<v-icon>mdi-skip-previous</v-icon>
<v-icon>mdi-coffee</v-icon>
*/
  computed: {
    currentPlayer() {
      if (!this.view.players) return null;
      return this.view.players[this.context.viewer]
    },
    currentPlayerIndex() {
      return this.context.viewer
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
