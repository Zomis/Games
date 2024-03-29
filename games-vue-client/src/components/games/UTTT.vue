<template>
  <div class="board-parent">
    <div class="board uttt-big-board">
      <div
        v-for="(area, areaIndex) in areas"
        :key="areaIndex"
        class="smaller-board"
      >
        <Map2D
          :width="3"
          :height="3"
          :grid="area.subs"
          :piece-exists="e => true"
        >
          <template v-slot:default="slotProps">
            <UrPiece
              :key="slotProps.key"
              :mouseover="highlight(slotProps)"
              :mouseleave="highlight(null)"
              class="piece"
              :class="{['piece-' + slotProps.tile.tile.owner]: true, highlighted: highlightedAreas[areaIndex]}"
              :onclick="generateOnClickFor(areaIndex)"
              :actionable="slotProps.tile.tile.actionable"
              :piece="slotProps.tile"
            />
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
  props: ["view", "actions"],
  components: {
    Map2D,
    UrPiece
  },
  data() {
    return {
      showRules: false,
      highlightedAreas: [false, false, false, false, false, false, false, false, false]
    }
  },
  computed: {
    areas() {
      if (!this.view) return []
      if (!this.view.boards) return []
      return this.view.boards.flatMap(e => e)
    }
  },
  methods: {
    highlight(props) {
      return () => {
        let highlightedAreas = [false, false, false, false, false, false, false, false, false]
        if (props !== null && props.tile !== null) {
          if (this.view.boards[props.tile.y][props.tile.x].owner === null) {
            highlightedAreas[props.tile.y * 3 + props.tile.x] = true;
          } else {
            for (let i = 0; i < 9; i++) {
              if (this.view.boards[Math.floor(i / 3)][Math.floor(i % 3)].owner === null) {
                highlightedAreas[i] = true;
              }
            }
          }
        }
        this.highlightedAreas = highlightedAreas;
      }
    },
    generateOnClickFor(areaIndex) {
      return (piece) => this.pieceClick(areaIndex, piece)
    },
    pieceClick(areaIndex, piece) {
      let global = this.boardTileToGlobal({ boardIndex: areaIndex, x: piece.x, y: piece.y })
      console.log("play on", global)
      this.actions.actionParameter("play", { x: global.x, y: global.y });
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
