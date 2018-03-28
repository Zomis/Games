<template>
  <div>
    <div>{{ game }} : {{ gameId }} Current player {{ ur.currentPlayer }} your index {{ yourIndex }}</div>
    <div>
      <button :disabled="!canPlaceNew" @click="action('move', 0)" class="placeNew">Place new</button>

      <div>Game: {{ ur }}</div>
      <div>Status: {{ gameOverMessage }}</div>
    </div>
    <div>
      <UrPlayerView v-bind:game="ur" v-bind:playerIndex="0" />
      <div class="ur-board gridview">
        <div class="gridview-inside">
          <UrFlower :x="0" :y="0" />
          <UrFlower :x="3" :y="1" />
          <UrFlower :x="0" :y="2" />
          <UrFlower :x="6" :y="0" />
          <UrFlower :x="6" :y="2" />

          <UrPiece v-for="piece in playerPieces"
            :key="piece.key"
            class="piece"
            :class="['piece-' + piece.player]"
            :x="piece.x" :y="piece.y" :id="piece.key"
            @:click="click(piece.position)">
          </UrPiece>
        </div>
      </div>
      <UrPlayerView v-bind:game="ur" v-bind:playerIndex="1" />
      <div class="ur-roll">
        <span>{{ ur.roll }}</span>
        <button :enabled="ur.roll <= 0" @click="action('roll', -1)" class="roll">Roll</button>
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
let ur = new games.net.zomis.games.ur.RoyalGameOfUr_init();
console.log(ur.toString());

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
      lastMove: 0,
      ur: ur,
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
    positionFor: function(x, y) {
      if (y === 1) {
        return 5 + x;
      }
      if (x < 4) {
        return 4 - x;
      }
      return 4 + 8 + 8 - x;
    },
    action: function(name, data) {
      let json = `v1:{ "game": "UR", "gameId": "${
        this.gameId
      }", "type": "move", "moveType": "${name}", "move": ${data} }`;
      Socket.send(json);
    },
    onClick: function(x, y) {
      console.log("OnClick in URView: " + x + ", " + y);
      this.action("move", this.positionFor(x, y));
    },
    messageEliminated(e) {
      console.log(`Recieved eliminated: ${JSON.stringify(e)}`);
      this.gameOverMessage = e;
    },
    messageMove(e) {
      console.log(`Recieved move: ${e.moveType}: ${e.move}`);
      if (e.moveType == "move") {
        this.ur.move_qt1dr2$(ur.currentPlayer, e.move, ur.roll);
      }
      // A move has been done - check if it is my turn.
    },
    messageState(e) {
      console.log(`MessageState: ${e.roll}`);
      if (e.roll) {
        this.ur.doRoll_za3lpa$(e.roll);
      }
    },
    messageIllegal(e) {
      console.log("IllegalMove: " + JSON.stringify(e));
    }
  },
  computed: {
    playerPieces: function() {
      let pieces = this.ur.piecesCopy;
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
    },
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
  border: 2px solid black;
}

.piece-1 {
  background-color: red;
  border: 2px solid black;
}

.piece-flower {
  background-image: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0NSIgaGVpZ2h0PSI0NSI+PGcgZmlsbD0ibm9uZSIgZmlsbC1ydWxlPSJldmVub2RkIiBzdHJva2U9IiMwMDAiIHN0cm9rZS13aWR0aD0iMS41IiBzdHJva2UtbGluZWNhcD0icm91bmQiIHN0cm9rZS1saW5lam9pbj0icm91bmQiPjxwYXRoIGQ9Ik0yMi41IDExLjYzVjZNMjAgOGg1IiBzdHJva2UtbGluZWpvaW49Im1pdGVyIi8+PHBhdGggZD0iTTIyLjUgMjVzNC41LTcuNSAzLTEwLjVjMCAwLTEtMi41LTMtMi41cy0zIDIuNS0zIDIuNWMtMS41IDMgMyAxMC41IDMgMTAuNSIgZmlsbD0iI2ZmZiIgc3Ryb2tlLWxpbmVjYXA9ImJ1dHQiIHN0cm9rZS1saW5lam9pbj0ibWl0ZXIiLz48cGF0aCBkPSJNMTEuNSAzN2M1LjUgMy41IDE1LjUgMy41IDIxIDB2LTdzOS00LjUgNi0xMC41Yy00LTYuNS0xMy41LTMuNS0xNiA0VjI3di0zLjVjLTMuNS03LjUtMTMtMTAuNS0xNi00LTMgNiA1IDEwIDUgMTBWMzd6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0iTTExLjUgMzBjNS41LTMgMTUuNS0zIDIxIDBtLTIxIDMuNWM1LjUtMyAxNS41LTMgMjEgMG0tMjEgMy41YzUuNS0zIDE1LjUtMyAyMSAwIi8+PC9nPjwvc3ZnPg==');
}

.gridview {
  background-color: cyan;
  width: 512px;
  height: 192px;
  margin-left: auto;
  margin-right: auto;
}

.gridview-inside {
  width: 512px;
  height: 192px;
  position: relative;
}

.piece {
  position: absolute;
  background-size: cover;
  border: 1px solid black;
  top: 0;
  left: 0;
  width: 64px;
  height: 64px;
  z-index: 2;
}

.piece-black {
  background-color: #ffffff;
}
</style>
