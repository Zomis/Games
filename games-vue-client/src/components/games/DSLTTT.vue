<template>
  <div class="game-dsl">
    <GameHead :gameInfo="gameInfo"></GameHead>
    <Map2D :width="3" :height="3" :grid="view.board" :clickHandler="onClick">
      <template v-slot:default="slotProps">
        <UrPiece
          :key="slotProps.key"
          :mouseover="doNothing" :mouseleave="doNothing"
          class="piece"
          :class="'piece-' + slotProps.tile.tile.owner"
          :onclick="pieceClick"
          :piece="slotProps.tile">
        </UrPiece>
      </template>
    </Map2D>
    <GameResult :gameInfo="gameInfo"></GameResult>
  </div>
</template>
<script>
import Map2D from "../common/Map2D";
import Socket from "../../socket";
import UrPiece from "../ur/UrPiece";
import GameHead from "./common/GameHead";
import GameResult from "./common/GameResult";
import { mapState } from "vuex";

export default {
  name: "DSLTTT",
  props: ["gameInfo", "showRules"],
  created() {
    Socket.$on("type:IllegalMove", this.messageIllegal); // TODO: Is this used?
  },
  beforeDestroy() {
    Socket.$off("type:IllegalMove", this.messageIllegal);
  },
  mounted() {
    Socket.send(
      `{ "type": "ViewRequest", "gameType": "${
        this.gameInfo.gameType
      }", "gameId": "${this.gameInfo.gameId}" }`
    );
  },
  components: {
    Map2D,
    GameHead,
    GameResult,
    UrPiece
  },
  methods: {
    doNothing: function() {},
    action: function(name, data) {
      if (Socket.isConnected()) {
        let json = `{ "gameType": "${this.gameInfo.gameType}", "gameId": "${
          this.gameInfo.gameId
        }", "type": "move", "moveType": "${name}", "move": ${JSON.stringify(
          data
        )} }`;
        Socket.send(json);
      }
    },
    pieceClick(data) {
      console.log(`onClick on DSLTTT pieceClick invoked: ${data.x}, ${data.y}`)
      this.action("play", { x: data.x, y: data.y });
    },
    onClick: function(x, y) {
      console.log(`onClick on DSLTTT invoked: ${x}, ${y}`)
      this.action("play", { x: x, y: y });
    },
    messageIllegal(e) {
      console.log("IllegalMove: " + JSON.stringify(e));
    }
  },
  computed: {
    ...mapState("DslGameState", {
      view(state) {
        return state.games[this.gameInfo.gameId].gameData.view;
      }
    })
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
