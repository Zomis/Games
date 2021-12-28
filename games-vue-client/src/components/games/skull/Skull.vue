<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(player, playerIndex) in view.players"
        :key="playerIndex"
      >
        <v-card
          class="player"
          :class="{ passed: player.pass, 'current-player': playerIndex == view.currentPlayer }"
        >
          <v-card-title>
            <PlayerProfile
              show-name
              :player="context.players[playerIndex]"
            />
          </v-card-title>
          <v-card-text>
            <span
              v-if="playerIndex == context.viewer && mustDiscard"
              class="discard-notice"
            >Choose a card to DISCARD</span>
            <CardZone v-if="Array.isArray(player.hand)">
              <Actionable
                v-for="(card, index) in player.hand"
                :key="index"
                button
                :actions="actions"
                class="list-complete-item"
                :actionable="'hand-' + card"
              >
                <v-icon :color="colors[card]">
                  {{ icons[card] }}
                </v-icon>
              </Actionable>
            </CardZone>
            <CardZone v-else>
              <Actionable
                v-for="(index) in player.hand"
                :key="index"
                button
                :actions="actions"
                class="list-complete-item"
                :actionable="'choosePlayer-' + playerIndex"
              >
                <v-icon>mdi-crosshairs-question</v-icon>
              </Actionable>
            </CardZone>

            <div>Bet</div>
            <div>{{ player.bet }}</div>
            <Actionable
              v-if="context.players[playerIndex].controllable"
              button
              :action-type="['pass', 'bet']"
              :actions="actions"
            >
              Bet/Pass
            </Actionable>

            <div>Points</div>
            <span>{{ player.points }}</span>
          </v-card-text>
        </v-card>

        <CardZone v-if="Array.isArray(player.board)">
          <Actionable
            v-for="(card, index) in player.board"
            :key="index"
            button
            :actions="actions"
            class="list-complete-item"
            :actionable="'choose-' + playerIndex"
          >
            <v-icon :color="colors[card]">
              {{ icons[card] }}
            </v-icon>
          </Actionable>
        </CardZone>
        <span v-else>
          <CardZone>
            <Actionable
              v-for="index in player.board"
              :key="index"
              button
              :actionable="'choose-' + playerIndex"
              class="list-complete-item"
              :actions="actions"
            >
              <v-icon>mdi-crosshairs-question</v-icon>
            </Actionable>
          </CardZone>
        </span>
        <CardZone>
          <v-icon
            v-for="(card, index) in player.chosen"
            :key="index"
            class="list-complete-item"
            :color="colors[card]"
          >
            {{ icons[card] }}
          </v-icon>
        </CardZone>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <p>{{ view.cardsTotal }} cards played</p>
        <p>{{ view.cardsChosenRemaining }} cards remaining to be chosen</p>
      </v-col>
    </v-row>
  </v-container>
</template>
<script>
import CardZone from "@/components/games/common/CardZone"
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Actionable from "@/components/games/common/Actionable"

export default {
  name: "Skull",
  props: ["view", "actions", "context"],
  components: {
    PlayerProfile, CardZone,
    Actionable
  },
  data() {
    return {
      colors: { FLOWER: 'green', SKULL: 'black' },
      icons: { FLOWER: 'mdi-flower', SKULL: 'mdi-skull' }
    }
  },
  computed: {
    mustDiscard() {
      let keys = Object.keys(this.actions.available)
      if (keys.length === 0) return false

      return keys.every(key => this.actions.available[key].actionType === "discard")
    }
  }
}
</script>
<style scoped>
@import "../../../assets/games-animations.css";
@import "../../../assets/active-player.css";

.discard-notice {
    color: red;
}

.v-card.passed {
    opacity: 0.5
}

.actionable {
    border-style: solid !important;
    border-width: thick !important;
    border-color: #ffd166 !important;
}
</style>
