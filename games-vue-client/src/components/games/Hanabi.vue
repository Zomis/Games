<template>
  <v-container fluid>
    <v-row>
      <v-col v-for="colorData in view.colors" :key="'colors-' + colorData.color">
        <v-card>
          <v-card-title>{{ colorData.color }}</v-card-title>
          <v-card-text>
            <transition-group name="list-complete" tag="div">
              <HanabiCard v-for="card in colorData.board" class="list-complete-item" :key="card.id" :card="card" />
            </transition-group>
            <v-row>Discard</v-row>
            <transition-group name="list-complete" tag="div">
              <HanabiCard v-for="card in colorData.discard" class="list-complete-item" :key="card.id" :card="card" />
            </transition-group>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row justify="center">
      <v-col md="auto" v-for="player in view.others" :key="'player-' + player.index" class="animate-all">
        <v-card :class="{ 'active-player': view.currentPlayer == player.index }" class="animate-all">
          <v-card-title>
            <span class="player-name">
              {{ player.index + 1 }}. {{ players[player.index].name }}
            </span>
          </v-card-title>
          <v-card-text>
            <transition-group name="list-complete" tag="div">
              <HanabiCard v-for="card in player.cards" class="list-complete-item" :key="card.id" :card="card" doubleView="true" />
            </transition-group>
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
    <v-row justify="center" class="translate-animation-wrapper">
      <transition name="translate-animation">
      <v-card :key="view.hand.index" class="animate-all player-hand" :class="{ 'active-player': view.currentPlayer == view.hand.index }">
        <v-card-title>
          <span class="player-name">
            {{ view.hand.index + 1 }}. {{ players[view.hand.index].name }} (You)
          </span>
        </v-card-title>
        <v-card-text>
          <transition-group name="list-complete" tag="div">
            <HanabiCard v-for="(card, cardIndex) in view.hand.cards" class="list-complete-item" :key="card.id" :card="card" :action="myTurn ? btnActions : false" :index="cardIndex" />
          </transition-group>
        </v-card-text>
      </v-card>
      </transition>
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
<style scoped>
@import "../../assets/games-style.css";

.animate-all {
  transition: all 1.5s ease;
}

.translate-animation-wrapper {
  position: relative;
  height: 180px;
}
.translate-animation-wrapper .player-hand {
  position: absolute;
}
.translate-animation-enter-active, .translate-animation-leave-active {
  transition: all 1s;
}
.translate-animation-enter, .translate-animation-leave-active {
  opacity: 0;
}
.translate-animation-enter {
  transform: translateX(400px);
}
.translate-animation-leave-active {
  transform: translateX(-400px);
}

.player-name {
  transition: text-shadow 1.5s ease;
}
.active-player .player-name {
  text-shadow: 3px 3px 5px #007F00;
}

.list-complete-item {
  transition: all 1s linear;
  display: inline-block !important;
  margin-right: 10px;
}
.list-complete-enter {
  opacity: 0;
  transform: translateY(30px);
}
.list-complete-leave-to {
  opacity: 0;
  transform: translateY(-30px);
}
.list-complete-leave-active {
  position: absolute;
}
</style>
