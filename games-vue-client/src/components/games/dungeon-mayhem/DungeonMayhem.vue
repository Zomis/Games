<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(player, playerIndex) in view.players"
        :key="playerIndex"
      >
        <v-card :class="{ ['color-' + player.character.color ]: true, currentPlayer: playerIndex == view.currentPlayer }">
          <PlayerProfile
            :player="context.players[playerIndex]"
            show-name
            :post-fix="'(' + player.character.className + ')'"
          />
          <p>
            Health: {{ player.health }} <v-icon v-if="player.protected" color="#6a0dad">
              mdi-account-lock
            </v-icon>
          </p>
          <p>Deck: {{ player.deck }}</p>
          <Actionable
            button
            :actionable="`target:player-${playerIndex};shield-null;discarded-null`"
            action-type="target"
            :actions="actions"
          >
            Target
          </Actionable>
          <p>Hand:</p>
          <div v-if="player.hand[0]">
            <CardZone>
              <DungeonMayhemCard
                v-for="(card, index) in player.hand"
                :key="index"
                :card="card"
                class="list-complete-item"
                :actionable="'play-' + card.name"
                :actions="actions"
              />
            </CardZone>
          </div>
          <CardZone v-else>
            <v-icon
              v-for="index in player.hand"
              :key="index"
              class="list-complete-item"
            >
              mdi-crosshairs-question
            </v-icon>
          </CardZone>

          <v-menu>
            <template v-slot:activator="{ on }">
              <v-btn v-on="on">
                {{ player.discard.length }} Discarded
              </v-btn>
            </template>
            <CardZone>
              <DungeonMayhemCard
                v-for="(card, index) in player.discard"
                :key="index"
                :card="card"
                class="list-complete-item"
                :actions="actions"
                :actionable="`target:player-${playerIndex};shield-null;discarded-${index}`"
              />
            </CardZone>
          </v-menu>

          <p>Played:</p>
          <CardZone>
            <DungeonMayhemCard
              v-for="(card, index) in player.played"
              :key="index"
              class="list-complete-item"
              :card="card"
              :actions="actions"
            />
          </CardZone>
          <p>Shields:</p>
          <CardZone>
            <DungeonMayhemCard
              v-for="(card, index) in player.shields"
              :key="index"
              :card="card"
              :actions="actions"
              class="list-complete-item"
              :actionable="`target:player-${playerIndex};shield-${index};discarded-null`"
            />
          </CardZone>
        </v-card>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <div
          v-for="(item, index) in view.stack"
          :key="index"
        >
          {{ item }}
        </div>
      </v-col>
    </v-row>
    <v-row>
      <v-card
        max-width="800"
        class="mx-auto"
      >
        <v-toolbar
          color="cyan"
          dark
        >
          <v-toolbar-title>Symbols</v-toolbar-title>
        </v-toolbar>

        <v-row no-gutters>
          <template v-for="(symbol, index) in symbolsInGame">
            <v-col
              :key="index"
              cols="4"
            >
              <v-card
                class="pa-2"
                outlined
                tile
              >
                <v-icon :color="symbol.color">
                  {{ symbol.icon }}
                </v-icon>
                <span>{{ symbol.text }}</span>
              </v-card>
            </v-col>
            <v-responsive
              v-if="index % 2 === 0"
              :key="`width-${index}`"
              width="100%"
            />
          </template>
        </v-row>
      </v-card>
    </v-row>
  </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import CardZone from "@/components/games/common/CardZone"
import DungeonMayhemCard from "./DungeonMayhemCard"
import Actionable from "@/components/games/common/Actionable"
import dungeonMayhemSymbols from "./dungeonMayhemSymbols"

export default {
    name: "DungeonMayhem",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile,
        Actionable,
        CardZone,
        DungeonMayhemCard
    },
    computed: {
        symbolsInGame: () => dungeonMayhemSymbols
    }
}
</script>
<style scoped>
@import "../../../assets/games-animations.css";

.actionable {
    border-style: solid !important;
    border-width: thick !important;
    border-color: #ffd166 !important;
}

.currentPlayer {
    background-color: #ddf9fd;
}
</style>