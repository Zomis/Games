<template>
  <v-container fluid>
    <div v-if="view">
      <CryptidHexGrid :hexGrid="view.hexGrid" :onClick="hexClick" />
    </div>
    <div v-if="view">
      {{ view.clue }}
      {{ view.placeCube }}
      {{ view.currentPlayer }}
      {{ selected }}
      <div v-for="(_, p) in context.players" :key="p">
        <player-profile :context="context" :playerIndex="p" />
        <v-btn @click="question(p)" :disabled="view.currentPlayer != context.viewer || view.placeCube || p == context.viewer">Question</v-btn>
      </div>
      <v-btn v-if="selected" @click="placeCube(selected)" :disabled="view.currentPlayer != context.viewer || !view.placeCube">Place Cube at {{ selected }}</v-btn>
      <v-btn v-if="selected" @click="search(selected)" :disabled="view.currentPlayer != context.viewer || view.placeCube">Search {{ selected }}</v-btn>
    </div>
  </v-container>
</template>
<script>
import PlayerProfile from "@/components/games/common/PlayerProfile"
import CryptidHexGrid from "./CryptidHexGrid"

export default {
  name: "Cryptid",
  props: ["view", "actions", "context"],
  components: {
    PlayerProfile, CryptidHexGrid
  },
  data() {
    return {
      selected: null,
    }
  },
  methods: {
    question(playerIndex) {
      console.log(playerIndex, this.selected);
      this.actions.actionParameter('question', { playerIndex, point: { q: this.selected.q, r: this.selected.r } });
    },
    placeCube(hex) {
      console.log(hex);
      this.actions.actionParameter('cube', this.selected);
    },
    search(hex) {
      console.log(hex);
      this.actions.actionParameter('search', this.selected);
    },
    hexClick(hex) {
      this.selected = hex;
      console.log(hex);
    }
  },
  /*
  computed: {
    boards() {
      if (!this.view) return [];
      if (!this.view.grid) return [];
      let boards = this.view.grid.boards
      let arr = [];
      for (var i = 0; i < 6; i++) {
        let row = i % 3 + 1;
        let col = Math.floor(i / 3) + 1;

        let flipped = this.view.grid.flipped[i] ? 'flipped' : '';
        arr.push({
          asset: assets[boards[i] - 1],
          clazz: `my-row-${row} my-col-${col} ${flipped}`
        });
      }
      return arr;
    },
    structures() {
      if (!this.view) return [];
      let structs = this.view.grid.structures;
      let arr = [];
      for (var s of structs) {
        var hex = oddq_to_axial({ x: s.pos.x, y: s.pos.y });
        arr.push({
          type: s.structure.type.substring(0, 2),
          clazz: s.structure.color,
          q: hex.q,
          r: hex.r
        });
      }
      console.log(arr);
      return arr;
    }
  }
  */
}
</script>
<style>
.board-parent {
  margin-left: auto;
  margin-right: auto;
  position: relative;
  width: 500px;
  height: 500px;
}
.board-parent img {
  position: absolute;
  float: left;
}
.board-parent img.flipped {
  transform: rotate(180deg);
}
.board-parent img.my-col-1 {
  left: 0;
}
.board-parent img.my-col-2 {
  left: 237px;
}
.board-parent img.my-row-1 {
  top: 0;
}
.board-parent img.my-row-2 {
  top: 138px;
}
.board-parent img.my-row-3 {
  top: 276px;
}
.board-parent div {
  position: absolute;
}
.test {
  position: absolute;
  left: 4px;
  top: 4px;
}
.board-parent .Blue {
  background-color: blue;
}
.board-parent .White {
  background-color: white;
}
.board-parent .Green {
  background-color: green;
}
.board-parent .Black {
  background-color: brown;
}
</style>