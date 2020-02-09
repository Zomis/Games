<template>
  <div class="board-parent">
    <div class="board uttt-big-board">
      <div class="smaller-board" v-for="(area, areaIndex) in areas" :key="areaIndex">
        <Map2D :width="3" :height="3" :grid="area.subs" :pieceExists="e => true">
          <template v-slot:default="slotProps">
            <UrPiece
              :key="slotProps.key"
              :mouseover="doNothing" :mouseleave="doNothing"
              class="piece"
              :class="'piece-' + slotProps.tile.tile.owner"
              :onclick="generateOnClickFor(areaIndex)"
              :actionable="actions.play && actions.play[`${Math.floor(areaIndex % 3) * 3 + slotProps.tile.x},${Math.floor(areaIndex / 3) * 3 + slotProps.tile.y}`]"
              :piece="slotProps.tile">
            </UrPiece>
          </template>
        </Map2D>
      </div>
    </div>
    <template v-if="showRules">
    <v-expansion-panels>
      <v-expansion-panel>
        <v-expansion-panel-header>Rules</v-expansion-panel-header>
        <v-expansion-panel-content>
          <v-card>
            <v-card-text>
              <ul>
                <li>Each turn, you mark one of the small squares.</li>
                <li>When you get three in a row on a small board, you’ve won that board.</li>
                <li>To win the game, you need to win three small boards in a row.</li>
              </ul>
            </v-card-text>
            <v-card-text>
              <p>You don’t get to pick which of the nine boards to play on. That’s determined by your opponent’s previous move. Whichever square he picks, that’s the board you must play in next. (And whichever square you pick will determine which board he plays on next.)</p>
              <p>What if my opponent sends me to a board that’s already been won? In that case, congratulations – you get to go anywhere you like, on any of the other boards. (This means you should avoid sending your opponent to an already-won board!)</p>
              <p>What if one of the small boards results in a tie? I recommend that the board counts for neither X nor O. But, if you feel like a crazy variant, you could agree before the game to count a tied board for both X and O.</p>
              <a href="http://mathwithbaddrawings.com/2013/06/16/ultimate-tic-tac-toe/">Description from mathwithbaddrawings.com</a>
            </v-card-text>
          </v-card>
        </v-expansion-panel-content>
      </v-expansion-panel>
    </v-expansion-panels>
    </template>
  </div>
</template>
<script>
import UrPiece from "../ur/UrPiece";
import Map2D from "@/components/common/Map2D";

export default {
  name: "UTTT",
  props: ["view", "actions", "onAction"],
  components: {
    Map2D,
    UrPiece
  },
  data() {
    return { showRules: false }
  },
  computed: {
    allowedPlay() {
      // let activeBoard = this.view.activeBoard
      let allowed = [true, true, true, true, true, true, true, true, true]
      return allowed
      // activeBoard == null || activeBoard == area || activeBoard!!.wonBy !== TTPlayer.NONE
    },
    areas() {
      if (!this.view) return []
      if (!this.view.boards) return []
      return this.view.boards.flatMap(e => e)
    }
  },
  methods: {
    doNothing: function() {},
    generateOnClickFor(areaIndex) {
      return (piece) => this.pieceClick(areaIndex, piece)
    },
    pieceClick(areaIndex, piece) {
      let global = this.boardTileToGlobal({ boardIndex: areaIndex, x: piece.x, y: piece.y })
      console.log("play on", global)
      this.onAction("play", global);
    },
    boardTileToGlobal(piece) {
      let x = (piece.boardIndex % 3) * 3 + piece.x;
      let y = Math.floor(piece.boardIndex / 3) * 3 + piece.y;
      return { x: x, y: y };
    }
  }
};
</script>
<style scoped>
@import "../../assets/games-style.css";

.uttt-big-board {
  width: 640px;
  height: 640px;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
  grid-gap: 10px;
  padding: 10px;
}

.smaller-board {
  position: relative;
  border: 3px solid black;
}

.smaller-board .pieces {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
}
</style>
