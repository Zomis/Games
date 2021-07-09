<template>
  <v-card>
    <v-card-text>
      <v-row justify="start">
        <v-col cols="4">
          <v-row>
            <v-col>
              <PlayerProfile
                :size="32"
                show-name
                :player="playerInfo"
              />
            </v-col>
          </v-row>
          <v-row>
            <v-col>
              <h1>{{ player.points }}</h1>
            </v-col>
          </v-row>
        </v-col>
        <v-col>
          <v-row
            justify="center"
            align="center"
          >
            <div
              v-for="moneyType in moneyTypes"
              :key="moneyType"
            >
              <div
                v-if="moneyType !== 'wildcards'"
                class="ma-1"
              >
                <span
                  class="resource"
                  :class="{ ['color-' + moneyType]: true, empty: !player.discounts[moneyType] }"
                >{{ player.discounts[moneyType] || 0 }}</span>
              </div>
              <div
                :class="{ discardable: controllable && actions.available['discardMoney-' + moneyType] }"
                @click="discard(moneyType)"
              >
                <span
                  class="gems"
                  :class="{ ['color-' + moneyType]: true, empty: !player.money[moneyType] }"
                >{{ player.money[moneyType] || 0 }}</span>
              </div>
            </div>
          </v-row>
        </v-col>
      </v-row>
      <p v-if="player.reserved">
        Reserved Cards: {{ player.reserved }}
      </p>
      <v-row justify="start">
        <v-col
          v-for="card in player.reservedCards"
          :key="card.id"
          cols="4"
        >
          <SplendorCard
            :card="card"
            :actions="actions"
          />
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import SplendorCard from "./SplendorCard"

export default {
  name: "SplendorPlayer",
  props: ["player", "controllable", "actions", "playerInfo"],
  components: { SplendorCard, PlayerProfile },
  methods: {
    discard(moneyType) {
      if (this.controllable) {
        this.actions.perform("discardMoney", "discardMoney-" + moneyType)
      }
    }
  },
  computed: {
    moneyTypes() {
      let allMoneys = [...Object.keys(this.player.discounts), ...Object.keys(this.player.money)];
      allMoneys.sort()
      return [...new Set(allMoneys)]
    }
  }
}
</script>
<style>
:root{
    --splendor-red: #ef476f;
    --splendor-blue: #118AB2;
    --splendor-green: #06D6A0;
    --splendor-black: #011627;
    --splendor-white: #f0e6ef;
    --splendor-yellow: #ffd166;
}
.empty {
    opacity: 0.2;
}
.resource, .gems {
    padding: 5px 10px;
    margin: 0px 5px;
    border-style: solid;
    border-width: thin;
    border-color: var(--splendor-black) !important;
}
.discardable .gems {
    border-width: thick !important;
    border-color: var(--splendor-red) !important;
    cursor: pointer;
}
.resource {
    border-radius: 20%;
}
.gems {
    border-radius: 100%;
}
.color-RED {
    background-color: var(--splendor-red) !important;
}
.color-BLUE {
    background-color: var(--splendor-blue) !important;
}
.color-GREEN {
    background-color: var(--splendor-green) !important;
}
.color-BLACK {
    background-color: var(--splendor-black) !important;
}
.color-WHITE {
    background-color: var(--splendor-white) !important;
}
.color-wildcards {
    background-color: var(--splendor-yellow) !important;
}
.color-RED, .color-BLUE, .color-BLACK {
    color: var(--splendor-white) !important;
}
.color-GREEN, .color-WHITE, .color-wildcards {
    color: var(--splendor-black) !important;
}
</style>
