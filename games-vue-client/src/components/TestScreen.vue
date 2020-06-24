<template>
  <v-card>
    <ActionLog :logEntries="logEntries" :context="context" />
    <h1>Hello World</h1>

    <v-card :key="view.hand.index" class="player-hand" :class="{ 'active-player': view.currentPlayer == view.hand.index }">
      <v-card-title>
        <span class="player-name">
          {{ view.hand.index + 1 }}. (You)
        </span>
      </v-card-title>
      <v-card-text>
        <v-row>
        <transition-group name="list-complete" tag="p">
          <v-col class="list-complete-item" md="auto" xs="auto" v-for="item in items" :key="item">
          <span>
            {{ item }}
          </span>
          </v-col>
        </transition-group>
        </v-row>
      </v-card-text>
    </v-card>

    <div id="list-complete-demo" class="demo">
      <v-btn v-on:click="shuffle">Shuffle</v-btn>
      <v-btn v-on:click="add">Add</v-btn>
      <v-btn v-on:click="addRemove">Add and Remove</v-btn>
      <v-btn v-on:click="remove">Remove</v-btn>
      <transition-group name="list-complete" tag="p">
        <span
          v-for="item in items"
          v-bind:key="item"
          class="list-complete-item"
        >
          {{ item }}
        </span>
      </transition-group>
    </div>

    <transition name="no-mode-translate-fade">
      <v-card :key="view.hand.index" class="player-hand" :class="{ 'active-player': view.currentPlayer == view.hand.index }">
        <v-card-title>
          <span class="player-name">
            {{ view.hand.index + 1 }}. (You)
          </span>
        </v-card-title>
        <v-card-text>
          <transition-group name="list-complete" tag="div" class="">
            <HanabiCard v-for="(card, cardIndex) in view.hand.cards" class="list-complete-item" :key="card.id" :card="card" :action="myTurn ? btnActions : false" :index="cardIndex" />
          </transition-group>
        </v-card-text>
      </v-card>

    </transition>
  </v-card>
</template>

<script>
import HanabiCard from "@/components/games/HanabiCard"
import ActionLog from "@/components/games/ActionLog"

function shuffle(a) {
    for (let i = a.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
}

export default {
  name: "TestScreen",
  components: { HanabiCard, ActionLog },
  data() {
    return {
      logEntries: [{
        highlights: [2],
        parts: [
          { type: "player", value: 0 },
          { type: "text", value: " changed the value by " },
          { type: "highlight", value: 2 },
          { type: "text", value: " to " },
          { type: "link", text: "a card", viewType: "card",
            value: {
              costs: { WHITE: 4 }, discount: { BLACK: 1 }, id: "2:2:B.WWWW", level: 2, points: 2
            }
          },
//          { type: "link", text: "something else", viewType: "number", value: 42 },
        ]
      }],
      context: {
        gameType: "Splendor",
        players: [{ name: "Simon", picture: "https://www.gravatar.com/avatar/434d24777c7a98f41c8e41b258589e3a?s=128&d=identicon" }]
      },
      items: [1,2,3,4,5,6,7,8,9],
      id: 6,
      nextNumber: 10,
      myTurn: true,
      view: {
        currentPlayer: 0,
        hand: {
          index: 0,
          cards: [
            { id: 1, colorKnown: false, valueKnown: true, color: null, value: 1 },
            { id: 2, colorKnown: false, valueKnown: true, color: null, value: 2 },
            { id: 3, colorKnown: false, valueKnown: true, color: null, value: 3 },
            { id: 4, colorKnown: false, valueKnown: true, color: null, value: 4 },
            { id: 5, colorKnown: false, valueKnown: true, color: null, value: 5 }
          ]
        }
      }
    }
  },
  methods: {
    btnActions(action, index) {
      let idx = parseInt(index.substring(index.indexOf('-') + 1), 10)
      console.log(action, idx)
      this.view.hand.cards.splice(idx, 1)
      this.view.hand.cards.push({ id: this.id, colorKnown: false, valueKnown: true, color: null, value: idx })
      this.id++
    },
    randomIndex() {
      return Math.floor(Math.random() * this.items.length)
    },
    add() {
      this.items.splice(this.randomIndex(), 0, this.nextNumber++)
    },
    addRemove() {
      this.remove()
      this.add()
    },
    remove() {
      this.items.splice(this.randomIndex(), 1)
    },
    shuffle() {
      let copy = this.items.slice()
      copy = shuffle(copy)
      this.items = copy
    }
  }
}
</script>
<style scoped>
.list-complete3-item {
  transition: all 30s linear;
  display: inline-block !important;
  margin-right: 10px;
}
.list-complete3-enter, .list-complete3-leave-to {
  opacity: 0;
}
.list-complete3-leave-active {
  position: absolute;
}




.list-complete-item {
  transition: all 1s linear;
  display: inline-block !important;
  margin-right: 10px;
}
.list-complete-enter, .list-complete-leave-to {
  opacity: 0;
}
.list-complete-leave-active {
  position: absolute;
}

.pos-absolute {
  position: absolute;
  top: 0;
  left: 0;
}

</style>
