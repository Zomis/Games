<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(player, playerIndex) in view.players"
        :key="playerIndex"
        cols="2"
      >
        <v-card
          :class="{ 'current-player': view.currentPlayer == playerIndex }"
          class="animate-all"
        >
          <v-card-title>
            <PlayerProfile
              show-name
              :player="context.players[playerIndex]"
            />
          </v-card-title>
          <v-card-text>
            <CardZone>
              <Actionable
                v-for="(card, cardIndex) in player.influence"
                :key="cardIndex"
                :actions="actions"
                :actionable="card"
              >
                <v-card>
                  <v-card-title>
                    <div>
                      {{ card }}
                    </div>
                  </v-card-title>
                </v-card>
              </Actionable>
              <v-card
                v-for="cardIndex in player.influenceCount"
                :key="cardIndex"
              >
                <v-card-title>
                  <div>
                    ???
                  </div>
                </v-card-title>
              </v-card>
              <v-card
                v-for="(card, cardIndex) in player.previousInfluence"
                :key="cardIndex"
              >
                <v-card-title>
                  <div>
                    {{ card }}
                  </div>
                </v-card-title>
              </v-card>
            </CardZone>
            <div>
              <Actionable
                v-if="actions.available[`players/${playerIndex}`]"
                button
                :actionable="`players/${playerIndex}`"
                :actions="actions"
              >
                {{ actions.available[`players/${playerIndex}`].actionType }}
              </Actionable>
            </div>
            <div>
              <h2>{{ player.coins }}</h2>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <p
          v-for="(task, taskIndex) in view.stack"
          :key="taskIndex"
        >
          {{ task }}
        </p>
      </v-col>
    </v-row>
    <v-row>
      {{ view.currentPlayer }}
    </v-row>
    <v-row>
      <v-col>
        <Actionable
          button
          :action-type="['perform']"
          :actions="actions"
        >
          Perform action
        </Actionable>
        <Actionable
          button
          :action-type="['approve']"
          :actions="actions"
        >
          Approve
        </Actionable>
        <Actionable
          button
          :action-type="['challenge']"
          :actions="actions"
        >
          Challenge
        </Actionable>
        <Actionable
          button
          :action-type="['counteract']"
          :actions="actions"
        >
          Counteract
        </Actionable>
        <Actionable
          button
          :action-type="['reveal']"
          :actions="actions"
        >
          Reveal
        </Actionable>
        <Actionable
          button
          :action-type="['putBack']"
          :actions="actions"
        >
          Put back
        </Actionable>
        <Actionable
          button
          :action-type="['lose']"
          :actions="actions"
        >
          Lose
        </Actionable>
      </v-col>
    </v-row>
  </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Actionable from "@/components/games/common/Actionable"
import CardZone from "@/components/games/common/CardZone"

export default {
    name: "Coup",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile, Actionable, CardZone
    },
}
</script>
<style scoped>
@import "../../assets/games-style.css";
@import "../../assets/games-animations.css";

.current-player {
    border-style: solid !important;
    border-width: thick !important;
    border-color: var(--splendor-yellow) !important;
}
</style>
