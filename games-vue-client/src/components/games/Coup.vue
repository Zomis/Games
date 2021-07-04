<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="(player, playerIndex) in view.players"
        :key="playerIndex"
        cols="2"
      >
        <v-card
          :class="{ 'current-player': view.currentPlayer == playerIndex, eliminated: !player.alive }"
          class="animate-all"
        >
          <v-card-title>
            <PlayerProfile
              show-name
              :context="context"
              :player-index="playerIndex"
            />
          </v-card-title>
          <v-card-text>
            <CardZone>
              <Actionable
                v-for="(card, cardIndex) in player.influence"
                :key="'influenceknown' + cardIndex"
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
                :key="'influence' + cardIndex"
              >
                <v-card-title>
                  <div>
                    ???
                  </div>
                </v-card-title>
              </v-card>
              <v-card
                v-for="(card, cardIndex) in player.previousInfluence"
                :key="'prev-' + cardIndex"
                class="eliminated"
              >
                <v-card-title>
                  <div>
                    {{ card }}
                  </div>
                </v-card-title>
              </v-card>
            </CardZone>
            <div>
              <v-btn
                v-if="player.actionable"
                @click="actions.choose(playerIndex, 'perform')"
                :actions="actions"
              >
                Target
              </v-btn>
            </div>
            <div>
              <h2>{{ player.coins }}</h2>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    <v-row :key="'buttons'">
      <v-col>
        <v-btn v-if="view.buttons.approve" @click="actions.actionParameter('approve', null)">Approve</v-btn>
        <v-btn v-if="view.buttons.challenge" @click="actions.actionParameter('challenge', null)">Challenge</v-btn>
        <v-btn v-if="view.buttons.reveal" @click="actions.actionParameter('reveal', null)">Reveal character</v-btn>
        <v-btn v-for="character in view.buttons.loseInfluence" :key="'char' + character" @click="actions.actionParameter('lose', character)">
          Lose {{ character }}
        </v-btn>
        <v-btn v-for="character in view.buttons.ambassadorPutBack" :key="'char' + character" @click="actions.actionParameter('putBack', character)">
          Put back {{ character }}
        </v-btn>
        <v-btn v-for="character in view.buttons.counter" :key="'char' + character" @click="actions.actionParameter('counteract', character)">
          Counteract by claiming {{ character }}
        </v-btn>
      </v-col>
    </v-row>
    <v-row :key="'actionstable'">
      <v-col>
        <h3>Actions</h3>
        <v-simple-table class="actions-table">
          <template v-slot:default>
            <thead>
              <tr>
                <th>Action</th>
                <th>Description</th>
                <th>Claim</th>
                <th>Can be blocked by</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(action, actionName) in view.actions" :key="actionName">
                <td>
                  <v-btn
                    :disabled="!action.allowed"
                    @click="actions.choose(action.name, 'perform')"
                  >
                    {{ action.name }}
                  </v-btn>
                </td>
                <td>{{ action.description }}</td>
                <td>{{ action.claim }}</td>
                <td>
                  <template v-for="(blockable, index) in action.blockable">
                    <span :key="'span' + index">{{ blockable }}</span><br :key="'br' + index" />
                  </template>
                </td>
              </tr>
            </tbody>
          </template>
        </v-simple-table>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <ActionLog
          :log-entries="view.stack"
          :context="context"
          title="Actions"
          :reversed="false"
        />
      </v-col>
    </v-row>
  </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import Actionable from "@/components/games/common/Actionable"
import CardZone from "@/components/games/common/CardZone"
import ActionLog from "@/components/games/ActionLog"

export default {
    name: "Coup",
    props: ["view", "actions", "context"],
    components: {
        PlayerProfile, Actionable, CardZone, ActionLog
    },
}
</script>
<style scoped>
@import "../../assets/games-style.css";
@import "../../assets/games-animations.css";

.actions-table.v-data-table th {
    text-align: center;
}

.eliminated {
    opacity: 0.4;
}
.current-player {
    border-style: solid !important;
    border-width: thick !important;
    border-color: var(--splendor-yellow) !important;
}
</style>
