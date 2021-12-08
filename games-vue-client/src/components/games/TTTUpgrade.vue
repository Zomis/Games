<template>
  <div class="game-tttupgrade">
    <v-row>
      <v-col>
        <PlayerProfile
          show-name
          :highlight="view.currentPlayer == 0"
          :context="context"
          :player-index="0"
        />
      </v-col>
      <v-col>
        <Shape
          v-for="(v, i) in players[0]"
          :key="v + '-' + i"
          :highlight="view.chosen === v && view.currentPlayer == 0"
          class="ma-2"
          type="square"
          :size="8 + 8 * v"
          color="blue"
          :on-click="() => playSize(0, v)"
        />
      </v-col>
    </v-row>
    <Map2D
      :grid="view.board"
      :click-handler="onClick"
      :piece-exists="e => true"
    >
      <template v-slot:default="slotProps">
        <Shape
          :key="slotProps.key"
          type="square"
          :test="slotProps"
          :size="slotProps.tile.tile.level === 0 ? 0 : 8 + 8 * slotProps.tile.tile.level"
          :color="slotProps.tile.tile.player == 'X' ? 'blue' : 'red'"
          :on-click="() => {}"
        />
      </template>
    </Map2D>
    <v-row>
      <v-col>
        <PlayerProfile
          show-name
          :highlight="view.currentPlayer == 1"
          :context="context"
          :player-index="1"
        />
      </v-col>
      <v-col>
        <Shape
          v-for="(v, i) in players[1]"
          :key="v + '-' + i"
          :highlight="view.chosen === v && view.currentPlayer == 1"
          class="ma-2"
          type="square"
          :size="8 + 8 * v"
          color="red"
          :on-click="() => playSize(1, v)"
        />
      </v-col>
    </v-row>
  </div>
</template>
<script>
import Map2D from "@/components/common/Map2D";
import Shape from "@/components/common/Shape";
import PlayerProfile from "@/components/games/common/PlayerProfile";

export default {
  name: "TTTUpgrade",
  props: ["view", "actions", "onAction", "context"],
  components: {
    PlayerProfile,
    Map2D,
    Shape
  },
  methods: {
    playSize(playerIndex, size) {
      if (this.view.currentPlayer !== playerIndex) return;
      console.log("playSize", playerIndex, size);
      this.actions.choose("play", size);
    },
    onClick(x, y) {
      console.log(x, y);
      this.actions.choose("play", { x: x, y: y });
      // this.actions.perform("play", `${x},${y}`);
    }
  },
  computed: {
    players() {
      if (!this.view.players) return [[], []];
      return this.view.players;
    },
    width() {
      if (!this.view.board) { return 0 }
      return this.view.board[0].length
    },
    height() {
      if (!this.view.board) { return 0 }
      return this.view.board.length
    }
  }
};
</script>
<style>
@import "../../assets/games-style.css";
</style>
