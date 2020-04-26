<template>
  <v-container fluid>
    <v-row>
      <v-col v-for="(board, index) in view.board" :key="'board-' + index">
        <v-card>
          <v-card-title>Board {{ index }}</v-card-title>
          <v-card-text>
            <v-row>
              <HanabiCard v-for="(card, cardIndex) in board" :key="cardIndex" :card="card" />
            </v-row>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row>
      <v-col v-for="player in view.others" :key="'player-' + player.index">
        <v-card>
          <v-card-title>Player {{ player.index }}</v-card-title>
          <v-card-text>
            <v-row>
              <HanabiCard v-for="(card, cardIndex) in player.cards" :key="cardIndex" :card="card" />
            </v-row>
          </v-card-text>
          <v-card-actions>
            <v-btn v-if="actions.GiveClue && actions.GiveClue['player-' + player.index]" @click="clue(player.index)">Give clue</v-btn>
            <template v-if="actionChoice && actionChoice.choices[0] === player.index">
              <v-btn v-for="(act, actIndex) in actions.GiveClue" @click="onAction('GiveClue', actIndex)" :key="actIndex">{{ actIndex }}</v-btn>
            </template>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>

    <v-row>
      <h2>Discard</h2>
      <!-- TODO: Filtered discards, with each color in its own pile -->
      <v-container>
        <v-row>
          <HanabiCard v-for="(card, cardIndex) in view.discard" :key="cardIndex" :card="card" />
        </v-row>
      </v-container>
    </v-row>
    <v-row>
      <!-- Buttons on each card: Discard, Play -->
      <p>Clues: {{ view.clues }}</p>
      <p>Fails: {{ view.fails }}</p>
    </v-row>
    <v-row>
      <h2>Hand</h2>
      <v-container>
        <v-row>
          <HanabiCard v-for="(card, cardIndex) in view.hand" :key="cardIndex" :card="card" :action="btnActions" :index="cardIndex" />
        </v-row>
      </v-container>
    </v-row>
    <v-row>
      <h2>Actions</h2>

    </v-row>
  </v-container>
</template>
<script>
import HanabiCard from "./HanabiCard"

export default {
  name: "Hanabi",
  props: ["view", "actions", "actionChoice", "onAction"],
  components: {
      HanabiCard
  },
  methods: {
    clue(index) {
      this.onAction("GiveClue", 'player-' + index);
    },
    btnActions(action, index) {
      this.onAction(action, index);
    }
  }
};
</script>
<style>
@import "../../assets/games-style.css";
</style>
