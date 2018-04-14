<template>
  <div class="game-uttt">
    <GameHead :game="game" :gameId="gameId" :players="players"></GameHead>
    <div class="board-parent">
      <div class="board uttt-big-board">
        <div class="smaller-board" v-for="boardIndex in 9">
          <div class="pieces pieces-bg">
            <div v-for="tileIndex in 9" class="piece piece-bg"
              :class="{ 'moveable': movesMade % 2 == yourIndex }"
              @click="onClick({ boardIndex: boardIndex - 1, tileIndex: tileIndex - 1 })">
            </div>
          </div>
          <div class="pieces player-pieces">
            <UrPiece v-for="piece in gamePieces"
              v-if="piece.boardIndex == boardIndex - 1"
              :key="piece.key"
              :mouseover="doNothing" :mouseleave="doNothing"
              class="piece"
              :class="'piece-' + piece.player"
              :piece="{ x: piece.tileIndex % 3, y: Math.floor(piece.tileIndex / 3) }">
            </UrPiece>
          </div>
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

function globalToBoardTile(position) {
  return {
    boardIndex: Math.floor(position.x / 3) + Math.floor(position.y / 3) * 3,
    tileIndex: Math.floor(position.x % 3) + Math.floor(position.y % 3) * 3
  };
}

function boardTileToGlobal(piece) {
  let x = (piece.boardIndex % 3) * 3 + piece.tileIndex % 3;
  let y =
    Math.floor(piece.boardIndex / 3) * 3 + Math.floor(piece.tileIndex / 3);
  return { x: x, y: y };
}

export default {
  name: "UTTT",
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
        }", "type": "move", "moveType": "${name}", "move": ${JSON.stringify(
          data
        )} }`;
        Socket.send(json);
      }
    },
    onClick: function(piece) {
      this.action("move", boardTileToGlobal(piece));
    },
    messageMove(e) {
      console.log(`Recieved move: ${e.moveType}: ${e.move}`);
      this.movesMade++;
      const boardTile = globalToBoardTile(e.move);
      this.gamePieces.push({
        x: e.move.x,
        y: e.move.y,
        player: e.player,
        boardIndex: boardTile.boardIndex,
        tileIndex: boardTile.tileIndex
      });
    },
    messageIllegal(e) {
      console.log("IllegalMove: " + JSON.stringify(e));
    }
  },
  computed: {
    moveableTiles: function() {
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

.uttt-big-board {
  width: 640px;
  height: 640px;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
}

.smaller-board {
  width: 196px;
  height: 196px;
  position: relative;
}

.smaller-board .pieces {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
}
</style>
