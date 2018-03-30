<template>
  <div>
    <h1>{{ game }} : {{ gameId }}</h1>
    <div>
      <div>{{ gameOverMessage }}</div>
    </div>
    <div class="board-parent">
      <UrPlayerView v-bind:game="ur" v-bind:playerIndex="0"
        :gamePieces="gamePieces"
        :onPlaceNewHighlight="onPlaceNewHighlight"
        :mouseleave="mouseleave"
        :onPlaceNew="placeNew" />

      <div class="ur-board">
        <div class="ur-pieces-bg">
          <div v-for="idx in 20" class="piece piece-bg">
          </div>
          <div class="piece-black" style="grid-area: 1 / 5 / 2 / 7"></div>
          <div class="piece-black" style="grid-area: 3 / 5 / 4 / 7"></div>
        </div>
        <div class="ur-pieces-flowers">
          <UrFlower :x="0" :y="0" />
          <UrFlower :x="3" :y="1" />
          <UrFlower :x="0" :y="2" />
          <UrFlower :x="6" :y="0" />
          <UrFlower :x="6" :y="2" />
        </div>

        <div class="ur-pieces-player">
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
              ur.canMove_qt1dr2$(ur.currentPlayer, piece.position, ur.roll)}"
            :piece="piece"
            :onclick="onClick">
          </UrPiece>
        </div>
      </div>
      <UrPlayerView v-bind:game="ur" v-bind:playerIndex="1"
       :gamePieces="gamePieces"
       :onPlaceNewHighlight="onPlaceNewHighlight"
       :mouseleave="mouseleave"
       :onPlaceNew="placeNew" />
      <UrRoll :roll="lastRoll" :usable="ur.roll < 0 && canControlCurrentPlayer" :onDoRoll="onDoRoll" />
    </div>
  </div>
</template>

<script>
import Socket from "../socket";
import UrPlayerView from "./ur/UrPlayerView";
import UrPiece from "./ur/UrPiece";
import UrRoll from "./ur/UrRoll";
import UrFlower from "./ur/UrFlower";

var games = require("../../../games-js/web/games-js");
if (typeof games["games-js"] !== "undefined") {
  // This is needed when doing a production build, but is not used for `npm run dev` locally.
  games = games["games-js"];
}
let urgame = new games.net.zomis.games.ur.RoyalGameOfUr_init();
console.log(urgame.toString());

function piecesToObjects(array, playerIndex) {
  var playerPieces = array[playerIndex].filter(i => i > 0 && i < 15);
  var arrayCopy = []; // Convert Int32Array to Object array
  playerPieces.forEach(it => arrayCopy.push(it));

  function mapping(position) {
    var y = playerIndex == 0 ? 0 : 2;
    if (position > 4 && position < 13) {
      y = 1;
    }
    var x =
      y == 1
        ? position - 5
        : position <= 4 ? 4 - position : 4 + 8 + 8 - position;
    return {
      x: x,
      y: y,
      player: playerIndex,
      key: playerIndex + "_" + position,
      position: position
    };
  }
  for (var i = 0; i < arrayCopy.length; i++) {
    arrayCopy[i] = mapping(arrayCopy[i]);
  }
  return arrayCopy;
}

export default {
  name: "RoyalGameOfUR",
  props: ["yourIndex", "game", "gameId"],
  data() {
    return {
      highlighted: null,
      lastRoll: 0,
      gamePieces: [],
      playerPieces: [],
      lastMove: 0,
      ur: urgame,
      gameOverMessage: null
    };
  },
  created() {
    if (this.yourIndex < 0) {
      Socket.send(
        `v1:{ "type": "observer", "game": "${this.game}", "gameId": "${
          this.gameId
        }", "observer": "start" }`
      );
    }
    Socket.$on("type:PlayerEliminated", this.messageEliminated);
    Socket.$on("type:GameMove", this.messageMove);
    Socket.$on("type:GameState", this.messageState);
    Socket.$on("type:IllegalMove", this.messageIllegal);
    this.playerPieces = this.calcPlayerPieces();
  },
  beforeDestroy() {
    Socket.$off("type:PlayerEliminated", this.messageEliminated);
    Socket.$off("type:GameMove", this.messageMove);
    Socket.$off("type:GameState", this.messageState);
    Socket.$off("type:IllegalMove", this.messageIllegal);
  },
  components: {
    UrPlayerView,
    UrRoll,
    UrFlower,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `v1:{ "game": "UR", "gameId": "${
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
          this.playerPieces = this.calcPlayerPieces();
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
      console.log(`Recieved eliminated: ${JSON.stringify(e)}`);
      this.gameOverMessage = e;
    },
    messageMove(e) {
      console.log(`Recieved move: ${e.moveType}: ${e.move}`);
      if (e.moveType == "move") {
        this.ur.move_qt1dr2$(this.ur.currentPlayer, e.move, this.ur.roll);
      }
      this.playerPieces = this.calcPlayerPieces();
      // A move has been done - check if it is my turn.
      console.log("After Move: " + this.ur.toString());
    },
    messageState(e) {
      console.log(`MessageState: ${e.roll}`);
      if (typeof e.roll !== "undefined") {
        this.ur.doRoll_za3lpa$(e.roll);
        this.rollUpdate(e.roll);
      }
      console.log("AfterState: " + this.ur.toString());
    },
    messageIllegal(e) {
      console.log("IllegalMove: " + JSON.stringify(e));
    },
    rollUpdate(rollValue) {
      this.lastRoll = rollValue;
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
    },
    calcPlayerPieces() {
      let pieces = this.ur.piecesCopy;
      this.gamePieces = this.ur.piecesCopy;
      let obj0 = piecesToObjects(pieces, 0);
      let obj1 = piecesToObjects(pieces, 1);
      let result = [];
      for (var i = 0; i < obj0.length; i++) {
        result.push(obj0[i]);
      }
      for (var i = 0; i < obj1.length; i++) {
        result.push(obj1[i]);
      }
      console.log(result);
      return result;
    }
  },
  computed: {
    canControlCurrentPlayer: function() {
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
      let result = piecesToObjects(
        [[resultPosition], [resultPosition]],
        this.highlighted.player
      );
      return result[0];
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
.piece-0 {
  background-color: blue;
}

.ur-pieces-player .piece {
  margin: auto;
  width: 48px;
  height: 48px;
}

.piece-1 {
  background-color: red;
}

.piece-flower {
  opacity: 0.5;
  background-image: url('../assets/ur/flower.svg');
  margin: auto;
}

.board-parent {
  position: relative;
}

.piece-bg {
  background-color: white;
  border: 1px solid black;
}

.ur-board {
  position: relative;
  width: 512px;
  height: 192px;
  min-width: 512px;
  min-height: 192px;
  overflow: hidden;
  border: 12px solid #6D5720;
  border-radius: 12px;
  margin: auto;
}

.ur-pieces-flowers {
  z-index: 60;
}

.ur-pieces-flowers, .ur-pieces-player,
 .ur-pieces-bg {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  grid-template-rows: repeat(3, 1fr);
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.ur-pieces-player .piece {
  z-index: 70;
}

.piece {
  background-size: cover;
  z-index: 40;
  width: 100%;
  height: 100%;
}

.piece-black {
  background-color: #7f7f7f;
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

.piece.highlighted {
  opacity: 0.5;
  box-shadow: 0 0 10px 8px black;
}

.side-out {
  flex-flow: row-reverse;
}

.moveable {
  cursor: pointer;
  animation: glow 1s infinite alternate;
}

@keyframes glow {
  from {
    box-shadow: 0 0 10px -10px #aef4af;
  }
  to {
    box-shadow: 0 0 10px 10px #aef4af;
  }
}

.fade-enter-active, .fade-leave-active {
  transition: opacity .5s;
}
.fade-enter, .fade-leave-to {
  opacity: 0;
}

</style>
