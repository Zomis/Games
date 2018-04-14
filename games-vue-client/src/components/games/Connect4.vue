<template>
  <div class="game-connect4">
    <GameHead :game="game" :gameId="gameId" :players="players"></GameHead>
    <div class="board-parent">
      <div class="board connect4-board">
        <div class="pieces pieces-bg">
          <div v-for="idx in 7*6" class="piece piece-bg"
            :class="{ 'moveable': moveableIndex[idx - 1] && movesMade % 2 == yourIndex }"
            @click="onClick({ x: (idx-1) % 7, y: (idx-1) / 7 })">
          </div>
        </div>
        <div class="pieces player-pieces">
          <UrPiece v-for="piece in gamePieces"
            :key="piece.key"
            :mouseover="doNothing" :mouseleave="doNothing"
            class="piece"
            :class="'piece-' + piece.player"
            :piece="piece">
          </UrPiece>
        </div>
      </div>
    </div>
    <GameResult :yourIndex="yourIndex"></GameResult>
  </div>
</template>
<script>
import Socket from "../../socket";
import UrPiece from "../ur/UrPiece";
import GameHead from "./common/GameHead";
import GameResult from "./common/GameResult";

export default {
  name: "Connect4",
  props: ["yourIndex", "game", "gameId", "players"],
  data() {
    return {
      movesMade: 0,
      gamePieces: [],
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
    Socket.$on("type:GameMove", this.messageMove);
    Socket.$on("type:IllegalMove", this.messageIllegal);
  },
  beforeDestroy() {
    Socket.$off("type:GameMove", this.messageMove);
    Socket.$off("type:IllegalMove", this.messageIllegal);
  },
  components: {
    GameHead,
    GameResult,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `{ "game": "${this.game}", "gameId": "${
          this.gameId
        }", "type": "move", "moveType": "${name}", "move": ${data} }`;
        Socket.send(json);
      }
    },
    onClick: function(piece) {
      this.action("move", piece.x);
    },
    messageMove(e) {
      console.log(`Recieved move: ${e.moveType}: ${e.move}`);
      let calculatedY = 5;
      while (this.gamePieces.find(p => p.y == calculatedY && p.x == e.move)) {
        calculatedY--;
      }
      this.movesMade++;
      this.gamePieces.push({ x: e.move, y: calculatedY, player: e.player });
    },
    messageIllegal(e) {
      console.log("IllegalMove: " + JSON.stringify(e));
    }
  },
  computed: {
    moveableIndex: function() {
      let result = [];
      for (let i = 0; i < 7 * 6; i++) {
        result.push(false);
      }
      for (let x = 0; x < 7; x++) {
        for (let y = 5; y >= 0; y--) {
          if (!this.gamePieces.find(e => e.y == y && e.x == x)) {
            result[y * 7 + x] = true;
            break;
          }
        }
      }
      return result;
    }
  }
};
</script>
<style>
@import "../../assets/games-style.css";

.connect4-board {
  width: 448px;
  height: 384px;
}

.game-connect4 .pieces {
  grid-template-columns: repeat(7, 1fr);
  grid-template-rows: repeat(6, 1fr);
}
</style>
