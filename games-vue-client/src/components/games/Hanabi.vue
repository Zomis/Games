<template>
  <v-container fluid>
    <v-row>
      <v-col v-for="colorData in view.colors" :key="'colors-' + colorData.color">
        <v-card>
          <v-card-title>{{ colorData.color }}</v-card-title>
          <v-card-text>
            <v-row>
              <HanabiCard v-for="(card, cardIndex) in colorData.board" :key="cardIndex" :card="card" />
            </v-row>
            <v-row>Discard</v-row>
            <v-row>
              <HanabiCard v-for="(card, cardIndex) in colorData.discard" :key="cardIndex" :card="card" />
            </v-row>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row>
      <v-col v-for="player in view.others" :key="'player-' + player.index">
        <v-card>
          <v-card-title><span :class="{ 'active-player': view.currentPlayer == player.index }">{{ players[player.index].name }}</span></v-card-title>
          <v-card-text>
            <v-row>
              <HanabiCard v-for="(card, cardIndex) in player.cards" :key="cardIndex" :card="card" doubleView="true" />
            </v-row>
          </v-card-text>
          <v-card-actions>
            <v-btn v-if="myTurn && actions.GiveClue && actions.GiveClue['player-' + player.index]" @click="clue(player.index)" :disabled="view.clues <= 0">
              Give clue
            </v-btn>
            <template v-if="myTurn && actionChoice && actionChoice.choices[0] === player.index">
              <v-btn v-for="(act, actIndex) in actions.GiveClue" @click="onAction('GiveClue', actIndex)" :key="actIndex">{{ actIndex }}</v-btn>
            </template>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>

    <v-row>
      <!-- Buttons on each card: Discard, Play -->
      <v-col>Clues: {{ view.clues }}</v-col>
      <v-col>Fails: {{ view.fails }}</v-col>
      <v-col>Cards Left: {{ view.cardsLeft }}</v-col>
      <v-col>Score: {{ view.score }}</v-col>
    </v-row>
    <v-row justify="center">
      <v-card>
        <v-card-title><span :class="{ 'active-player': view.currentPlayer == view.hand.index }">{{ players[view.hand.index].name }} (You)</span></v-card-title>
        <v-card-text>
          <v-row>
            <HanabiCard v-for="(card, cardIndex) in view.hand.cards" :key="cardIndex" :card="card" :action="myTurn ? btnActions : false" :index="cardIndex" />
          </v-row>
        </v-card-text>
      </v-card>
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
  props: ["view", "actions", "actionChoice", "onAction", "players"],
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
  },
  computed: {
    myTurn() {
      return this.view.currentPlayer == this.view.hand.index
    }
  }
};
</script>
<style>
@import "../../assets/games-style.css";

.active-player {
  text-shadow: 3px 3px 5px #007F00;
}
</style>
