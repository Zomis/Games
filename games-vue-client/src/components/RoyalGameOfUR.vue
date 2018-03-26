<template>
  <div>
    <div>{{ game }} : {{ gameId }} Current player {{ currentPlayer }} your index {{ yourIndex }} rolled: {{ ur.roll }}</div>
    <div>
      <button :enabled="canPlaceNew" @click="action('move', 0)" class="placeNew">Place new</button>
      <button :enabled="ur.roll <= 0" @click="action('roll', -1)" class="roll">Roll</button>
      <div>Score: {{ posAt[0][15] }} vs. {{ posAt[1][15] }}. Remaining to be placed: {{ posAt[0][0] }} and {{ posAt[1][0] }}</div>
      <div>Game: {{ ur }}</div>
      <div>Status: {{ gameOverMessage }}</div>
    </div>
  </div>
</template>

<script>
import Socket from "../socket";

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
    console.log("Creating Vue Component...");
    Socket.$on("type:PlayerEliminated", this.messageEliminated);
    Socket.$on("type:GameMove", this.messageMove);
    Socket.$on("type:GameState", this.messageState);
    Socket.$on("type:IllegalMove", this.messageIllegal);
    console.log("Created Vue Component - Done");
  },
  beforeDestroy() {
    Socket.$off("type:PlayerEliminated", this.messageEliminated);
    Socket.$off("type:GameMove", this.messageMove);
    Socket.$off("type:GameState", this.messageState);
    Socket.$off("type:IllegalMove", this.messageIllegal);
  },
  components: {},
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
    getPiece: function(player, position) {
      var y = player == 0 ? 0 : 2;
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
        name: player
      };
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
    posAt: function() {
      let result = [];
      let positions = this.ur.piecesCopy;
      for (var player = 0; player < positions.length; player++) {
        result[player] = [];
        for (var i = 0; i <= 15; i++) {
          result[player].push(0);
        }
        for (var pos = 0; pos < positions[player].length; pos++) {
          let value = positions[player][pos];
          result[player][value] += 1;
        }
      }
      return result;
      // posAt[0][15]
    },
    currentPlayer: function() {
      return this.ur.currentPlayer;
    },
    canPlaceNew: function() {
      return this.ur.currentPlayer == this.playerIndex;
    },
    pieces: function() {
      let result = [];
      for (var i = 0; i <= 2; i += 2) {
        result.push({ x: 0, y: i, name: "flower" });
        result.push({ x: 6, y: i, name: "flower" });
        result.push({ x: 4, y: i, name: "black" });
        result.push({ x: 5, y: i, name: "exit" });
      }
      result.push({ x: 3, y: 1, name: "flower" });

      let pieces = this.ur.piecesCopy;
      for (var player = 0; player < pieces.length; player++) {
        for (var piece = 0; piece < pieces[player].length; piece++) {
          let value = pieces[player][piece];
          console.log("value at " + player + ", " + piece + " is: " + value);
          if (value !== 0 && value !== 15) {
            result.push(this.getPiece(player, value));
          }
        }
      }
      return result;
    }
  }
};
</script>

<style>
.piece-0 {
  background-image: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0NSIgaGVpZ2h0PSI0NSI+PGcgZmlsbD0ibm9uZSIgZmlsbC1ydWxlPSJldmVub2RkIiBzdHJva2U9IiMwMDAiIHN0cm9rZS13aWR0aD0iMS41IiBzdHJva2UtbGluZWNhcD0icm91bmQiIHN0cm9rZS1saW5lam9pbj0icm91bmQiPjxwYXRoIGQ9Ik0yMiAxMGMxMC41IDEgMTYuNSA4IDE2IDI5SDE1YzAtOSAxMC02LjUgOC0yMSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0yNCAxOGMuMzggMi45MS01LjU1IDcuMzctOCA5LTMgMi0yLjgyIDQuMzQtNSA0LTEuMDQyLS45NCAxLjQxLTMuMDQgMC0zLTEgMCAuMTkgMS4yMy0xIDItMSAwLTQuMDAzIDEtNC00IDAtMiA2LTEyIDYtMTJzMS44OS0xLjkgMi0zLjVjLS43My0uOTk0LS41LTItLjUtMyAxLTEgMyAyLjUgMyAyLjVoMnMuNzgtMS45OTIgMi41LTNjMSAwIDEgMyAxIDMiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJNOS41IDI1LjVhLjUuNSAwIDEgMS0xIDAgLjUuNSAwIDEgMSAxIDB6bTUuNDMzLTkuNzVhLjUgMS41IDMwIDEgMS0uODY2LS41LjUgMS41IDMwIDEgMSAuODY2LjV6IiBmaWxsPSIjMDAwIi8+PC9nPjwvc3ZnPg==');
}

.piece-1 {
  background-image: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0NSIgaGVpZ2h0PSI0NSI+PHBhdGggZD0iTTIyLjUgOWMtMi4yMSAwLTQgMS43OS00IDQgMCAuODkuMjkgMS43MS43OCAyLjM4QzE3LjMzIDE2LjUgMTYgMTguNTkgMTYgMjFjMCAyLjAzLjk0IDMuODQgMi40MSA1LjAzLTMgMS4wNi03LjQxIDUuNTUtNy40MSAxMy40N2gyM2MwLTcuOTItNC40MS0xMi40MS03LjQxLTEzLjQ3IDEuNDctMS4xOSAyLjQxLTMgMi40MS01LjAzIDAtMi40MS0xLjMzLTQuNS0zLjI4LTUuNjIuNDktLjY3Ljc4LTEuNDkuNzgtMi4zOCAwLTIuMjEtMS43OS00LTQtNHoiIHN0cm9rZT0iIzAwMCIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPjwvc3ZnPg==');
}

.piece-black {
  background-color: #ffffff;
}
</style>
