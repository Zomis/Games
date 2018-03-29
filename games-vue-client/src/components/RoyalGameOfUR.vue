<template>
  <div>
    <div>{{ game }} : {{ gameId }} Current player {{ ur.currentPlayer }} your index {{ yourIndex }}</div>
    <div>
      <button :disabled="!canPlaceNew" @click="action('move', 0)" class="placeNew">Place new</button>

      <div>{{ gameOverMessage }}</div>
    </div>
    <div class="board-parent">
      <UrPlayerView v-bind:game="ur" v-bind:playerIndex="0"
        :gamePieces="gamePieces"
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
          <UrPiece v-for="piece in playerPieces"
            :key="piece.key"
            class="piece"
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
       :onPlaceNew="placeNew" />
      <div class="ur-roll">
        <span>{{ ur.roll }}</span>
        <button :disabled="ur.roll >= 0 || ur.currentPlayer != yourIndex" @click="action('roll', -1)" class="roll">Roll</button>
      </div>
    </div>
  </div>
</template>

<script>
import Socket from "../socket";
import UrPlayerView from "./ur/UrPlayerView";
import UrPiece from "./ur/UrPiece";
import UrFlower from "./ur/UrFlower";

let games = require("../../../games-js/web/games-js");
let urgame = new games.net.zomis.games.ur.RoyalGameOfUr_init();
console.log(urgame.toString());

//  ur.doRoll();
//  if (ur.isMoveTime) {
//    var player = ur.currentPlayer;
//    var roll = ur.roll;
//      if (ur.move_qt1dr2$(ur.currentPlayer, m, roll)) {

export default {
  name: "RoyalGameOfUR",
  props: ["yourIndex", "game", "gameId"],
  data() {
    return {
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
    UrFlower,
    UrPiece
  },
  methods: {
    action: function(name, data) {
      let json = `v1:{ "game": "UR", "gameId": "${
        this.gameId
      }", "type": "move", "moveType": "${name}", "move": ${data} }`;
      Socket.send(json);
        this.playerPieces = this.calcPlayerPieces();
    },
    placeNew: function(playerIndex) {
      if (this.canPlaceNew) {
        this.action("move", 0);
      }
    },
    onClick: function(piece) {
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
    },
    messageState(e) {
      console.log(`MessageState: ${e.roll}`);
      if (typeof e.roll !== "undefined") {
        this.ur.doRoll_za3lpa$(e.roll);
      }
    },
    messageIllegal(e) {
      console.log("IllegalMove: " + JSON.stringify(e));
    },
    calcPlayerPieces() {
      let pieces = this.ur.piecesCopy;
      this.gamePieces = this.ur.piecesCopy;
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
          var typeofm = typeof arrayCopy[i];
          arrayCopy[i] = mapping(arrayCopy[i]);
        }
        return arrayCopy;
      }
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
    canPlaceNew: function() {
      return (
        this.ur.currentPlayer == this.yourIndex &&
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
  /* width: 50vw; */
  /* height: 50vw; */
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
  /* background-color: #ddf9fd; */
  /* box-shadow: inset 2px 2px 0 rgba(255, 255, 255, 0.05), inset -2px -2px 0 #665235; */
  /* box-shadow: 0px 0px 0px 6px rgba(255, 255, 255, 0.05); */
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
}

.side {
  display: flex;
  flex-flow: row;
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

</style>
