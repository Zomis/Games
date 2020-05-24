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
              <div v-for="card in colorData.discard" class="list-complete-item discarded-card ma-1" :key="card.id" :class="'color-' + card.color">
                <span>{{ card.value }}</span>
              </div>
            </transition-group>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row justify="center">
      <v-col md="auto" v-for="player in otherPlayers" :key="'player-' + player.index" class="animate-all">
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
            <v-menu v-model="showMenu[player.index]" offset-y bottom z-index="100" :close-on-content-click="false">
              <template v-slot:activator="{ on }">
                <v-btn @click="clue(player.index)" :disabled="!myTurn || view.clues <= 0" v-on="on">
                  Give clue
                </v-btn>
              </template>
              <v-btn v-for="(act, actIndex) in clueOptions" :key="actIndex"
                   :class="[actIndex.includes('color-') ? actIndex : '']" @click="onAction('GiveClue', actIndex)">
                 {{ actIndex }}
              </v-btn>
            </v-menu>
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

    <v-row justify="center" class="translate-animation-wrapper" v-if="view.hand">
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

    <v-snackbar v-model="snackbar">
      {{snackbarText}}
    </v-snackbar>
  </v-container>
</template>

<script>
import HanabiCard from "./HanabiCard"

export default {
  name: "Hanabi",
  props: ["view", "actions", "actions2", "actionChoice", "onAction", "players"],
  components: {
      HanabiCard
  },
  methods: {
    clue(index) {
      this.actions2.resetTo("GiveClue", index);
    },
    btnActions(action, index) {
      this.onAction(action, index);
    }
  },
  watch: {
    actionChoice(val) {
      if (!val) {
        console.log("actionChoice cleared, resetting showMenu.");
        this.showMenu = [false, false, false, false, false];
      }
    },
    deckEmpty(val) {
      this.snackbar = val
    }
  },
  data() {
    console.log("players is", this.players);
    return {
      showMenu: [false, false, false, false, false], // One for each player
      snackbar: false,
      snackbarText: 'Last round'
    }
  },
  computed: {
    otherPlayers() {
      if (!this.view.hand) return this.view.others;
      let myIndex = this.view.hand.index;
      return [...this.view.others.slice(myIndex), ...this.view.others.slice(0, myIndex)]
    },
    clueOptions() {
      if (!this.actions.GiveClue) return [];
      return Object.keys(this.actions.GiveClue).filter(key => !key.includes('player'))
        .reduce((obj, key) => ({ ...obj, [key]: this.actions.GiveClue[key] }), {});
    },
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

.discarded-card {
  padding: 5px 10px;
  margin: 0px 5px;
  border-style: solid;
  border-width: thin;
  border-color: black !important;
  border-radius: 20%;
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

.discarded-card {
    color: black !important;
}
</style>
