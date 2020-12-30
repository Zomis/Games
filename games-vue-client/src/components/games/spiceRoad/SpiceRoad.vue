<template>
  <v-container>
    <v-row>
      <v-col cols="1">
        <v-card><v-card-title>{{ view.pointsDeck }}</v-card-title></v-card>
      </v-col>
      <v-col>
        <CardZone
          class="row spiceroad-action-cards"
        >
          <v-col
            v-for="card in view.pointCards"
            :key="card.id"
            class="list-complete-item"
          >
            <SpiceRoadCard
              :key="card.id"
              :card="card"
              :actions="actions"
            />
          </v-col>
        </CardZone>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="1">
        <v-card><v-card-title>{{ view.actionDeck }}</v-card-title></v-card>
      </v-col>
      <v-col>
        <CardZone
          class="row spiceroad-action-cards"
        >
          <v-col
            v-for="card in view.actionCards"
            :key="card.id"
            class="list-complete-item"
          >
            <SpiceRoadCard
              :key="card.id"
              :card="card"
              :actions="actions"
            />
          </v-col>
        </CardZone>
      </v-col>
    </v-row>
    <v-row
      v-for="player in view.players"
      :key="player.index"
    >
      <v-card>
        <v-card-text>
          <v-row>
      <v-col>
        <v-row>
          <v-col>
            <PlayerProfile
              show-name
              :context="context"
              :player-index="player.index"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            {{ player.points }} points
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <Actionable
              button
              :action-type="['pass']"
              :actions="actions"
            >
              Pass
            </Actionable>
          </v-col>
        </v-row>
      </v-col>
      <v-col>
        <SpiceRoadResources :caravan="player.caravan" />
      </v-col>
      <v-col>
        <CardZone
          class="row spiceroad-action-cards"
        >
          <v-col
            v-for="card in player.hand"
            :key="card.id"
            class="list-complete-item"
          >
            <SpiceRoadCard
              :key="card.id"
              :card="card"
              :actions="actions"
            />
          </v-col>
        </CardZone>
      </v-col>
      <v-col>
        <CardZone
          class="row spiceroad-action-cards"
          :style="{ opacity: 0.5 }"
        >
          <v-col
            v-for="card in player.discard"
            :key="card.id"
            class="list-complete-item"
          >
            <SpiceRoadCard
              :key="card.id"
              :card="card"
              :actions="actions"
            />
          </v-col>
        </CardZone>
      </v-col>
      </v-row>
      </v-card-text>
    </v-card>
    </v-row>
    <v-row>
      <v-col
        v-for="(amount, index) in view.coins"
        :key="index"
      >
        <span :class="{['coin-' + index]: true}">
          {{ amount }}
        </span>
      </v-col>
    </v-row>
  </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import CardZone from "@/components/games/common/CardZone"
import Actionable from "@/components/games/common/Actionable"
import SpiceRoadResources from "./SpiceRoadResources"
import SpiceRoadCard from "./SpiceRoadCard"

export default {
    name: "SpiceRoad",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile, Actionable, CardZone, SpiceRoadResources, SpiceRoadCard
    },
}
</script>
<style>
:root{
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