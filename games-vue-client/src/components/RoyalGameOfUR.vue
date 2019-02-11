<template>
  <div class="game-ur">
    <GameHead :gameType="gameType" :gameId="gameId" :players="players"></GameHead>
    <div class="board-parent">
      <UrPlayerView v-bind:game="ur" v-bind:playerIndex="0"
        :gamePieces="gamePieces"
        :onPlaceNewHighlight="onPlaceNewHighlight"
        :class="{ opponent: !canControlPlayer[0] }"
        :mouseleave="mouseleave"
        :onPlaceNew="placeNew" />

      <div class="board ur-board">
        <div class="pieces pieces-bg">
          <div v-for="idx in 20" class="piece piece-bg">
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
          <transition name="fade">
            <UrPiece v-if="destination !== null" :piece="destination" class="piece highlighted"
            :mouseover="doNothing" :mouseleave="doNothing"
            :class="{['piece-' + destination.player]: true}">
            </UrPiece>
          </transition>
          <UrPiece v-for="piece in playerPieces"
            :key="piece.key"
            class="piece"
            :mouseover="mouseover" :mouseleave="mouseleave"
            :class="{['piece-' + piece.player]: true, 'moveable':
              ur.isMoveTime && piece.player == ur.currentPlayer &&
              ur.canMove_qt1dr2$(ur.currentPlayer, piece.position, ur.roll),
              opponent: !canControlCurrentPlayer}"
            :piece="piece"
            :onclick="onClick">
          </UrPiece>
        </div>
      </div>
      <UrPlayerView v-bind:game="ur" v-bind:playerIndex="1"
       :gamePieces="gamePieces"
       :onPlaceNewHighlight="onPlaceNewHighlight"
       :class="{ opponent: !canControlPlayer[1] }"
       :mouseleave="mouseleave"
       :onPlaceNew="placeNew" />
      <UrRoll :roll="lastRoll" :usable="ur.roll < 0 && canControlCurrentPlayer" :onDoRoll="onDoRoll" />
    </div>
    <GameResult :gameId="gameId" :gameType="gameType" :yourIndex="yourIndex" :players="players"></GameResult>
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
import Socket from "../socket";
import UrPlayerView from "./ur/UrPlayerView";
import UrPiece from "./ur/UrPiece";
import UrRoll from "./ur/UrRoll";
import UrFlower from "./ur/UrFlower";
import GameHead from "./games/common/GameHead";
import GameResult from "./games/common/GameResult";
import { mapState } from "vuex";

// TODO: Place mapping and piecesToObjects in a common JS file, shared with store
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

export default {
  name: "RoyalGameOfUR",
  props: ["yourIndex", "gameType", "gameId", "players", "showRules"],
  data() {
    return {
      highlighted: null
    };
  },
  created() {
    if (this.yourIndex < 0) {
      Socket.send(
        `v1:{ "type": "observer", "game": "${this.gameType}", "gameId": "${
          this.gameId
        }", "observer": "start" }`
      );
    }
    Socket.$on("type:IllegalMove", this.messageIllegal);
    this.$store.dispatch("UR/calcPlayerPieces", this.gameId);
  },
  beforeDestroy() {
    Socket.$off("type:IllegalMove", this.messageIllegal);
  },
  components: {
    GameHead,
    GameResult,
    UrPlayerView,
    UrRoll,
    UrFlower,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `{ "game": "${this.gameType}", "gameId": "${
          this.gameId
        }", "type": "move", "moveType": "${name}", "move": ${data} }`;
        Socket.send(json);
      } else {
        console.log(
          "Before Action: " + name + ":" + data + " - " + this.ur.toString()
        );
        if (name === "roll") {
          let rollResult = this.ur.doRoll();
          this.rollUpdate(rollResult);
        } else {
          console.log(
            "move: " + name + " = " + data + " curr " + this.ur.currentPlayer
          );
          var moveResult = this.ur.move_qt1dr2$(
            this.ur.currentPlayer,
            data,
            this.ur.roll
          );
          console.log("result: " + moveResult);
          this.$store.dispatch("UR/calcPlayerPieces", this.gameId);
        }
        console.log(this.ur.toString());
      }
    },
    placeNew: function(playerIndex) {
      if (this.canPlaceNew) {
        this.action("move", 0);
      }
    },
    onClick: function(piece) {
      if (piece.player !== this.ur.currentPlayer) {
        return;
      }
      if (!this.ur.isMoveTime) {
        return;
      }
      console.log("OnClick in URView: " + piece.x + ", " + piece.y);
      this.action("move", piece.position);
    },
    messageEliminated(e) {
      if (this.gameType != e.gameType || this.gameId != e.gameId) {
        return;
      }
      console.log(`Recieved eliminated: ${JSON.stringify(e)}`);
      if (this.yourIndex == e.player) {
        this.gameOverMessage = e;
      }
    },
    messageIllegal(e) {
      if (this.gameType != e.gameType || this.gameId != e.gameId) {
        return;
      }
      console.log("IllegalMove: " + JSON.stringify(e));
    },
    onDoRoll() {
      this.action("roll", -1);
    },
    onPlaceNewHighlight(playerIndex) {
      if (playerIndex !== this.ur.currentPlayer) {
        return;
      }
      this.highlighted = { player: playerIndex, position: 0 };
    },
    mouseover(piece) {
      if (piece.player !== this.ur.currentPlayer) {
        return;
      }
      this.highlighted = piece;
    },
    mouseleave() {
      this.highlighted = null;
    }
  },
  computed: {
    ...mapState("UR", {
      ur(state) {
        return state.games[this.gameId].gameData.ur;
      },
      lastRoll(state) {
        return state.games[this.gameId].gameData.lastRoll;
      },
      playerPieces(state) {
        return state.games[this.gameId].gameData.playerPieces;
      },
      gamePieces(state) {
        return state.games[this.gameId].gameData.gamePieces;
      }
    }),
    canControlPlayer: function() {
      return [
        this.yourIndex == 0 || !Socket.isConnected(),
        this.yourIndex == 1 || !Socket.isConnected()
      ];
    },
    canControlCurrentPlayer: function() {
      if (this.ur.isFinished) {
        return false;
      }
      return this.ur.currentPlayer == this.yourIndex || !Socket.isConnected();
    },
    destination: function() {
      if (this.highlighted === null) {
        return null;
      }
      if (!this.ur.isMoveTime) {
        return null;
      }
      if (
        !this.ur.canMove_qt1dr2$(
          this.ur.currentPlayer,
          this.highlighted.position,
          this.ur.roll
        )
      ) {
        return null;
      }
      let resultPosition = this.highlighted.position + this.ur.roll;
      if (resultPosition >= 15) {
        return null;
      }
      let result = piecesToObjects(
        [[resultPosition], [resultPosition]],
        this.highlighted.player
      );
      return result[0];
    },
    playerVs: function() {
      if (typeof this.players !== "object") {
        return "local game";
      }
      return this.players[0] + " vs. " + this.players[1];
    },
    canPlaceNew: function() {
      return (
        this.canControlCurrentPlayer &&
        this.ur.canMove_qt1dr2$(this.ur.currentPlayer, 0, this.ur.roll)
      );
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
