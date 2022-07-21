<template>
  <v-container fluid>
    <v-row>
      <v-col
        v-for="colorData in view.colors"
        :key="'colors-' + colorData.color"
      >
        <v-card>
          <v-card-title>{{ colorData.color }}</v-card-title>
          <v-card-text>
            <CardZone>
              <HanabiCard
                v-for="card in colorData.board"
                :key="card.id"
                class="animate"
                :card="card"
                :highlight="view.lastAction[card.id] === 'play'"
              />
            </CardZone>
            <v-divider />
            <v-row>Discard</v-row>
            <CardZone>
              <div
                v-for="card in colorData.discard"
                :key="card.id"
                class="discarded-card animate ma-1"
                :class="{ ['color-' + card.color]: true, highlight: view.lastAction[card.id] === 'discard', failHighlight: view.lastAction[card.id] === 'fail' }"
              >
                <span>{{ card.value }}</span>
              </div>
            </CardZone>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row justify="center">
      <v-col
        v-for="player in otherPlayers"
        :key="'player-' + player.index"
        md="auto"
        class="animate-all"
      >
        <v-card
          :class="{ 'current-player': view.currentPlayer == player.index }"
          class="player animate-all"
        >
          <v-card-title>
            <PlayerProfile
              :player="context.players[player.index]"
              :size="32"
              show-name
            />
          </v-card-title>
          <v-card-text>
            <CardZone>
              <HanabiCard
                v-for="card in player.cards"
                :key="card.id"
                class="list-complete-item animate"
                :card="card"
                double-view="true"
                :highlight="view.lastAction[card.id] === 'clue' || highlights[card.id]"
              />
            </CardZone>
          </v-card-text>
          <v-card-actions>
            <v-menu
              v-model="showMenu[player.index]"
              offset-y
              bottom
              z-index="100"
              :close-on-content-click="false"
            >
              <template v-slot:activator="{ on }">
                <v-btn
                  :disabled="!myTurn || view.clues <= 0"
                  @click="clue(player.index)"
                  v-on="on"
                >
                  Give clue
                </v-btn>
              </template>
              <v-btn
                v-for="(act, actIndex) in view.actions.clueOptions"
                :key="actIndex"
                :class="{ ['color-' + actIndex]: view.actions.colors }"
                @mouseover="highlightCards(act)"
                @mouseleave="highlightCards([])"
                @click="actions.choose('GiveClue', actIndex)"
              >
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
        <transition
          name="number-transition"
          mode="out-in"
        >
          <p :key="view.clues">
            {{ view.clues }}
          </p>
        </transition>
      </v-col>
      <v-col>
        <span>Fails</span>
        <transition
          name="number-transition"
          mode="out-in"
        >
          <p :key="view.fails">
            {{ view.fails }} / {{ view.maxFails }}
          </p>
        </transition>
      </v-col>
      <v-col>
        <span>Cards Left</span>
        <transition
          name="number-transition"
          mode="out-in"
        >
          <p :key="view.cardsLeft">
            {{ view.cardsLeft }}
          </p>
        </transition>
      </v-col>
      <v-col>
        <span>Score</span>
        <transition
          name="number-transition"
          mode="out-in"
        >
          <p :key="view.score">
            {{ view.score }}
          </p>
        </transition>
        <span>{{ view.scoreDescription }}</span>
      </v-col>
    </v-row>

    <v-row
      v-if="view.hand"
      justify="center"
      class="translate-animation-wrapper"
    >
      <transition name="translate-animation">
        <v-card
          :key="view.hand.index"
          class="animate-all player player-hand"
          :class="{ 'current-player': view.currentPlayer == view.hand.index }"
        >
          <v-card-title>
            <PlayerProfile
              :player="context.players[view.hand.index]"
              :size="32"
              show-name
              post-fix="(You)"
            />
          </v-card-title>
          <v-card-text>
            <CardZone>
              <HanabiCard
                v-for="(card, cardIndex) in view.hand.cards"
                :key="card.id"
                class="animate"
                :card="card"
                :action="myTurn ? btnActions : false"
                :index="cardIndex"
                :highlight="view.lastAction[card.id] === 'clue'"
              />
            </CardZone>
          </v-card-text>
        </v-card>
      </transition>
    </v-row>

    <v-snackbar v-model="snackbar">
      {{ snackbarText }}
    </v-snackbar>
  </v-container>
</template>

<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import CardZone from "@/components/games/common/CardZone"
import HanabiCard from "./HanabiCard"

export default {
  name: "Hanabi",
  props: ["view", "actions", "context"],
  components: {
    CardZone,
    PlayerProfile,
    HanabiCard
  },
  methods: {
    clue(index) {
      this.actions.resetTo("GiveClue", index);
    },
    highlightCards(cards) {
      let highlights = {};
      cards.forEach(c => {
        highlights[c] = true;
      });
      this.highlights = highlights;
      console.log("highlight", cards, highlights);
    },
    btnActions(action, index) {
      this.actions.perform(action, index);
    }
  },
  watch: {
    actionChoice(val) {
      if (!val) {
        this.showMenu = [false, false, false, false, false];
      }
    },
    deckEmpty(val) {
      this.snackbar = val
    }
  },
  data() {
    return {
      highlights: [],
      showMenu: [false, false, false, false, false], // One for each player
      snackbar: false,
      snackbarText: 'Last round'
    }
  },
  computed: {
    actionChoice() {
      return this.actions.chosen;
    },
    otherPlayers() {
      if (Object.keys(this.view.hand).length === 0) return this.view.others;
      let myIndex = this.view.hand.index;
      return [...this.view.others.slice(myIndex), ...this.view.others.slice(0, myIndex)]
    },
    clueOptions() {
      if (!this.actions.available) return [];
      let prefix = "giveclue-";
      return Object.keys(this.actions.available).filter(key => key.includes(prefix))
        .reduce((obj, key) => ({ ...obj, [key.substring(prefix.length)]: this.actions.available[key] }), {});
    },
    myTurn() {
      return this.view.hand && this.view.currentPlayer == this.view.hand.index
    },
    deckEmpty() {
      return this.view.cardsLeft == 0
    }
  }
};
</script>
<style scoped>
@import "../../assets/active-player.css";
@import "../../assets/games-style.css";
@import "../../assets/games-animations.css";

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
.discarded-card.highlight {
  border: 1px solid yellowgreen !important;
  box-shadow: 0px 0px 5px 6px yellowgreen !important;
}
.discarded-card.failHighlight {
  border: 1px solid red !important;
  box-shadow: 0px 0px 5px 6px red !important;
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
