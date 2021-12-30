<template>
  <div class="backgammon">
    <div class="board">
      <div class="left-bin">
        <div class="top-row">
          <div
            v-for="i in 6"
            :key="i"
            class="arrow-down"
          />
        </div>
        <div class="bottom-row"> 
          <div
            v-for="i in 6"
            :key="i"
            class="arrow-up"
          />
        </div>
      </div> 
      <div class="middle-bar" />
      <div class="right-bin">
        <div class="top-row">
          <div
            v-for="i in 6"
            :key="i"
            class="arrow-down"
          />
        </div>
        <div class="bottom-row"> 
          <div
            v-for="i in 6"
            :key="i"
            class="arrow-up"
          />
        </div>
      </div>
      <div
        v-for="(piece, i) in pieces"
        :key="i"
        :style="{ left: piece.x + 'px', top: piece.y + 'px' }"
        :class="'player-' + piece.playerIndex"
        class="piece"
        @mouseover="mouseOver(piece.pos)"
        @mouseleave="mouseLeave(piece.pos)"
        @click="pieceClick(piece.pos)" />
    </div>

    <v-btn @click="actions.actionParameter('roll', null)" :disabled="!view.actions.roll">
      Roll
    </v-btn>
    {{ view.dice }}
    <v-btn v-for="(roll, i) in view.actions.dice" :key="i" @click="actions.choose('move', roll)">Use die {{ roll }}</v-btn>
    <p v-if="view.discardedDice.length > 0">Unusable dice was: {{ view.discardedDice }}</p>
  </div>
</template>
<script>
export default {
  name: "Backgammon",
  props: ["view", "actions", "context"],
  data() {
    return {
      highlighted: null
    }
  },
  methods: {
    mouseOver(pos) {
      this.highlighted = pos;
    },
    mouseLeave() {
      this.highlighted = null;
    },
    pieceClick(pos) {
      let moves = this.view.actions.piece;
      if (pos === 'bar') {
        this.actions.choose('move', 0);
        return
      }
      pos = parseInt(pos, 10);
      let pathPos = this.view.viewer == 0 ? pos + 1 : 23 - pos + 1;
      console.log(pos, pathPos, moves);
      if (moves.indexOf(pathPos) >= 0) {
        this.actions.choose('move', pathPos);
      }
    }
  },
  computed: {
    pieces() {
      if (!this.view.board) { return [] }
      let arr = [];
      
      if (this.highlighted !== null) {
        // arr.push({ x: 272, y: 100*playerIndex + 38*i, playerIndex: pos.playerIndex, pos: 0 });
      }
      for (let playerIndex = 0; playerIndex <= 1; playerIndex++) {
        let pos = this.view.middle[playerIndex];
        for (let i = 0; i < pos.count; i++) {
          arr.push({ x: 272, y: 100 + 200*playerIndex + 38*i, playerIndex: pos.playerIndex, pos: 'bar' });
        }
      }

      for (let posIndex in this.view.board) {
        let pos = this.view.board[posIndex];
        if (pos.count > 0) {
          let isLeft = posIndex >= 6 && posIndex <= 17;
          let isTop = posIndex >= 12;
          let left = isLeft ? 17 : 333;
          let top = isTop ? 0 : 420;
          let space = isTop ? 38 : -38;
          let showIndex = isTop ? posIndex : 11 - posIndex;

          // left: 40*x + 17 / 333
          // top: 0+n / 420-n
          for (let i = 0; i < pos.count; i++) {
            arr.push({ x: left + 40*(showIndex % 6), y: top + space*i, playerIndex: pos.playerIndex, pos: posIndex });
          }
        }
      }
      return arr;
    }
  }
}
</script>
<style scoped>
.board {
  width: 610px;
  height: 480px;
  position: relative;
  background-color: #fff1de;
  box-sizing: border-box;
  border: 10px solid black;
  border-radius: 10px;
  margin: 10px auto 0px auto;
  padding: 0px;
}

.left-bin {
  position: relative;
  left: 15px;
  top: 0px;
}

.right-bin {
  position: relative;
  left: 330px;
  top: -389px;
}

.arrow-down {
  width: 0px; 
  height: 0px; 
  border-left: 20px solid transparent;
  border-right: 20px solid transparent;
  border-top: 195px solid #b58763;
  border-bottom: 0px solid;
  display: inline;
  float: left;
}

.arrow-down:nth-child(2n) {
  border-top: 195px solid #d9c1a3;
}

.left-bin .bottom-row {
  clear: left;
}

.right-bin .top-row {
  clear: left;
}

.right-bin .bottom-row {
  clear: left;
}

.bottom-row {
  position: relative;
  top: 70px
}

.arrow-up {
  width: 0px; 
  height: 0px; 
  border-top: 0px solid;
  border-left: 20px solid transparent;
  border-right: 20px solid transparent;
  border-bottom: 195px solid #b58763;
  display: inline;
  float: left;
}

.arrow-up:nth-child(2n+1) {
  border-bottom: 195px solid #d9c1a3;
}

.middle-bar {
  width: 20px;
  height: 460px;
  padding: 5px;
  background-color: black;
  position: absolute;
  left: 280px;
  top: 0px;
}

.piece {
  width: 36px;
  height: 36px;
  background-color: #fff;
  opacity: 1.0;
  position: absolute;
  border-radius: 18px;
  z-index: 1;
  box-shadow: rgba(0, 0, 0, 0.35) 0px 5px 15px;
}

.piece.player-0 {
  background-color: #33337f;
}
.piece.player-1 {
  background-color: #7f3333;
}
</style>