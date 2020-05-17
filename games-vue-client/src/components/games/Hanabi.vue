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
            <v-divider />
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
              <span>{{ player.index + 1 }}.</span>
              <v-avatar :size="32">
                  <img
                      :src="players[player.index].picture"
                      :alt="players[player.index].name" />
              </v-avatar>
              <span>{{ players[player.index].name }}</span>
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
              <v-btn v-for="(act, actIndex) in actions.GiveClue" :class="[actIndex.includes('color-') ? actIndex : '']" @click="onAction('GiveClue', actIndex)" :key="actIndex">{{ actIndex }}</v-btn>
            </template>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>

    <v-row>
      <!-- Buttons on each card: Discard, Play -->
      <v-col>
        <span>Clues</span>
        <transition name="number-transition" mode="out-in"><p :key="view.clues">{{ view.clues }}</p></transition>
      </v-col>
      <v-col>
        <span>Fails</span>
        <transition name="number-transition" mode="out-in"><p :key="view.fails">{{ view.fails }} / {{ view.maxFails }}</p></transition>
      </v-col>
      <v-col>
        <span>Cards Left</span>
        <transition name="number-transition" mode="out-in"><p :key="view.cardsLeft">{{ view.cardsLeft }}</p></transition>
      </v-col>
      <v-col>
        <span>Score</span>
        <transition name="number-transition" mode="out-in"><p :key="view.score">{{ view.score }}</p></transition>
      </v-col>
    </v-row>

    <v-row justify="center" class="translate-animation-wrapper">
      <transition name="translate-animation">
      <v-card :key="view.hand.index" class="animate-all player-hand" :class="{ 'active-player': view.currentPlayer == view.hand.index }">
        <v-card-title>
          <span class="player-name">
            <span>{{ view.hand.index + 1 }}.</span>
            <v-avatar :size="32">
              <img
                :src="players[view.hand.index].picture"
                :alt="players[view.hand.index].name" />
            </v-avatar>
            <span>{{ players[view.hand.index].name }} (You)</span>
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
     <v-snackbar v-model="snackbar">
        {{snackbarText}}
     </v-snackbar>
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
  watch: {
    deckEmpty(val) {
      this.snackbar = val
    }
  },
  data() {
    return {
      snackbar: false,
      snackbarText: 'Last round'
    }
  },
  computed: {
    myTurn() {
      return this.view.currentPlayer == this.view.hand.index
    },
    deckEmpty() {
      return this.view.cardsLeft == 0
    }
  }
};
</script>
<style scoped>
@import "../../assets/games-style.css";

.color-RED {
 background-color: #ef476f !important;
}
.color-BLUE {
  background-color: #118AB2 !important;
}
.color-GREEN {
 background-color: #06D6A0 !important;
}
.color-YELLOW {
  background-color: #FFD166 !important;
}
.color-WHITE {
  background-color: #F5F5F5 !important;
}
.color-RAINBOW {
  background-color: #ff7f00 !important;
}

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


.number-transition-enter-active {
  animation: bounce-in .75s;
}
.number-transition-leave-active {
  animation: bounce-in .75s reverse;
}
@keyframes bounce-in {
  0% {
    transform: scale(0);
  }
  50% {
    transform: scale(2.0);
  }
  100% {
    transform: scale(1);
  }
}
</style>
