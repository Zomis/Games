<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(player, playerIndex) in view.players"
        :key="playerIndex"
      >
        <v-card :class="{ currentPlayer: playerIndex == view.currentPlayer, eliminated: player.dice.length === 0 }">
          <v-card-title>
            <PlayerProfile
              show-name
              :player="context.players[playerIndex]"
            />
          </v-card-title>
          <v-card-text>
            <CardZone v-if="Array.isArray(player.dice)">
              <span
                v-for="(value, index) in player.dice"
                :key="index"
                class="list-complete-item"
              >
                {{ value }}
              </span>
            </CardZone>
            <CardZone v-else>
              <v-icon
                v-for="(index) in player.dice"
                :key="index"
                class="list-complete-item"
              >
                mdi-crosshairs-question
              </v-icon>
            </CardZone>

            <Actionable
              v-if="context.players[playerIndex].controllable"
              button
              actionable="liar"
              :actions="actions"
            >
              Liar
            </Actionable>
            <Actionable
              v-if="context.players[playerIndex].controllable"
              button
              actionable="spotOn"
              :actions="actions"
            >
              Spot-On!
            </Actionable>
            <Actionable
              v-if="context.players[playerIndex].controllable"
              button
              :action-type="['bet']"
              :actions="actions"
              sticky-menu
            >
              Bet
            </Actionable>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row v-if="view.bet">
      <v-col>
        Current Bet: {{ view.bet.amount }}x {{ view.bet.value }} by <PlayerProfile
          show-name
          :player="context.players[view.better]"
        />
      </v-col>
    </v-row>
    <v-row>
      Config: {{ view.config }}
    </v-row>
  </v-container>
</template>
<script>
import CardZone from "@/components/games/common/CardZone"
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Actionable from "@/components/games/common/Actionable"

export default {
    name: "LiarsDice",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile, CardZone,
        Actionable
    }
}
</script>
<style scoped>
.eliminated {
    opacity: 0.5
}
.actionable {
    border-style: solid !important;
    border-width: thick !important;
    border-color: #ffd166 !important;
}

.currentPlayer {
    background-color: #ddf9fd;
}
</style>
