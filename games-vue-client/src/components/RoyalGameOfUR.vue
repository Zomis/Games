<template>
  <div class="game-ur">
    <div class="board-parent">
      <UrPlayerView :view="view" :playerIndex="0"
        :onPlaceNewHighlight="onPlaceNewHighlight"
        :class="{ opponent: !canControlPlayer[0] }"
        :mouseleave="mouseleave"
        :onPlaceNew="placeNew" />

      <div class="board ur-board">
        <div class="pieces pieces-bg">
          <div v-for="idx in 20" class="piece piece-bg" :key="idx">
          </div>
          <div class="piece-black" style="grid-area: 1 / 5 / 2 / 7"></div>
          <div class="piece-black" style="grid-area: 3 / 5 / 4 / 7"></div>
        </div>
        <div class="pieces ur-pieces-flowers">
          <UrFlower :x="0" :y="0" />
          <UrFlower :x="3" :y="1" />
          <UrFlower :x="0" :y="2" />
          <UrFlower :x="6" :y="0" />
          <UrFlower :x="6" :y="2" />
        </div>

        <div class="pieces player-pieces">
          <UrPiece v-if="destination !== null" :piece="destination" class="highlighted"
            :mouseover="doNothing" :mouseleave="doNothing"
            :class="{['piece-' + destination.player]: true}">
          </UrPiece>
          <UrPiece v-for="piece in playerPieces"
            :key="piece.key"
            class="piece"
            :mouseover="mouseover" :mouseleave="mouseleave"
            :class="{['piece-' + piece.player]: true, 'moveable':
              view.isMoveTime && piece.player === view.currentPlayer,
              opponent: !canControlCurrentPlayer}"
            :piece="piece"
            :onclick="onClick">
          </UrPiece>
        </div>
      </div>
      <UrPlayerView :view="view" :playerIndex="1"
       :onPlaceNewHighlight="onPlaceNewHighlight"
       :class="{ opponent: !canControlPlayer[1] }"
       :mouseleave="mouseleave"
       :onPlaceNew="placeNew" />
      <UrRoll :roll="lastRoll" :usable="view.roll < 0 && canControlCurrentPlayer" :onDoRoll="onDoRoll" />
    </div>
    <v-expansion-panel v-if="showRules" expand>
      <v-expansion-panel-content>
        <div slot="header">Objective</div>
        <v-card>
          <v-card-text>
            Two players are fighting to be the first player who races all their 7 pieces to the exit.
            Only player 1 can use the top row, only player 2 can use the bottom row. Both players share the middle row.
          </v-card-text>
        </v-card>
      </v-expansion-panel-content>
      <v-expansion-panel-content>
        <div slot="header">Dice</div>
        <v-card>
          <v-card-text>
            Players take turns in rolling the four dice. Each die can be 1 or 0. Then you move a piece a number of steps that equals the sum of these four dice.
          </v-card-text>
        </v-card>
      </v-expansion-panel-content>
      <v-expansion-panel-content>
        <div slot="header">Flowers</div>
        <v-card>
          <v-card-text>
            Five tiles are marked with flowers. When a piece lands on a flower the player get to roll again.
            As long as a tile is on a flower another piece may not knock it out (only relevant for the middle flower).
          </v-card-text>
        </v-card>
      </v-expansion-panel-content>
    </v-expansion-panel>
  </div>
</template>

<script>
import UrPlayerView from "./ur/UrPlayerView";
import UrPiece from "./ur/UrPiece";
import UrRoll from "./ur/UrRoll";
import UrFlower from "./ur/UrFlower";

function mapping(position, playerIndex) {
  let y = playerIndex == 0 ? 0 : 2;
  if (position > 4 && position < 13) {
    y = 1;
  }
  let x = 0;
  if (y == 1) {
    x = position - 5;
  } else {
    x = position <= 4 ? 4 - position : 4 + 8 + 8 - position;
  }
  return {
    x: x,
    y: y,
    player: playerIndex,
    key: playerIndex + "_" + position,
    position: position
  };
}
function piecesToObjects(array, playerIndex) {
  let playerPieces = array[playerIndex].filter(i => i > 0 && i < 15);
  return Array.from(playerPieces).map(e => mapping(e, playerIndex));
}

function determinePlayerPieces(view) {
  let pieces = view.pieces;
  if (!pieces) {
    return [];
  }
  let obj0 = piecesToObjects(pieces, 0);
  let obj1 = piecesToObjects(pieces, 1);
  let result = [];
  var i;
  for (i = 0; i < obj0.length; i++) {
    result.push(obj0[i]);
  }
  for (i = 0; i < obj1.length; i++) {
    result.push(obj1[i]);
  }
  return result;
}

export default {
  name: "RoyalGameOfUR",
  props: ["view", "actions"],
  data() {
    return {
      showRules: false,
      playerPieces: [],
      highlighted: null
    };
  },
  components: {
    UrPlayerView,
    UrRoll,
    UrFlower,
    UrPiece
  },
  methods: {
    doNothing() {},
    placeNew() { // playerIndex parameter
      if (this.canPlaceNew) {
        this.actions.perform("move", 0);
      }
    },
    onClick(piece) {
      if (piece.player !== this.view.currentPlayer) {
        return;
      }
      if (!this.isMoveTime) {
        return;
      }
      console.log("OnClick in URView: " + piece.x + ", " + piece.y);
      this.actions.perform("move", `${piece.position}`);
    },
    onDoRoll() {
      this.actions.perform("roll", "roll");
    },
    onPlaceNewHighlight(playerIndex) {
      if (playerIndex !== this.view.currentPlayer) {
        return;
      }
      this.highlighted = { player: playerIndex, position: 0 };
    },
    canMove(from) {
      console.log("Can move from", from)
//            this.view.pieces[this.view.currentPlayer][ + this.view.roll
      return true
    },
    mouseover(piece) {
      if (piece.player !== this.view.currentPlayer) {
        return;
      }
      this.highlighted = piece;
    },
    mouseleave() {
      this.highlighted = null;
    }
  },
  watch: {
    view(newView) {
      if (!newView) return []
      this.playerPieces = determinePlayerPieces(newView)
      console.log("view change", newView, "resulted in", this.playerPieces)
    }
  },
  computed: {
    lastRoll() {
      return this.view.lastRoll;
    },
    isMoveTime() {
      return this.view.roll > 0;
    },
    canControlPlayer: function() {
      return [true, true]; // TODO: Add information about which players can be controlled
    },
    canControlCurrentPlayer() {
      return true;
    },
    destination() {
      if (this.highlighted === null) {
        return null;
      }
      if (!this.isMoveTime) {
        return null;
      }
      if (!this.canMove(this.highlighted.position)) {
        return null;
      }
      let resultPosition = this.highlighted.position + this.view.roll;
      if (resultPosition >= 15) {
        return null;
      }
      let result = piecesToObjects(
        [[resultPosition], [resultPosition]],
        this.highlighted.player
      );
      return result[0];
    },
    canPlaceNew() {
      return this.canControlCurrentPlayer && this.canMove(0);
    }
  }
};
</script>

<style>
@import "../assets/games-style.css";

.piece-flower {
  opacity: 0.5;
  background-image: url("../assets/ur/flower.svg");
  margin: auto;
}

.ur-board {
  width: 512px;
  height: 192px;
}

.ur-pieces-flowers {
  z-index: 60;
}

.player-view {
  width: 512px;
  height: 50px;
  margin: auto;
  display: flex;
  flex-flow: row;
  justify-content: space-between;
  align-items: center;
}

.side {
  display: flex;
  flex-flow: row;
}

.side-out {
  flex-flow: row-reverse;
}

.game-ur .pieces {
  grid-template-columns: repeat(8, 1fr);
  grid-template-rows: repeat(3, 1fr);
}
</style>
