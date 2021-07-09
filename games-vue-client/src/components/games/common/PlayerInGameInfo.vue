<template>
  <v-col
    :cols="cols"
    class="player-in-game-info"
  >
    <template v-if="displayStyle === 'avatars' || displayStyle === 'vs'">
      <v-tooltip bottom>
        <template v-slot:activator="{ on }">
          <v-avatar
            :size="48"
            :class="[winResultClass, eliminatedOpacityClass]"
            v-on="on"
          >
            <img
              :src="player.picture"
              :alt="player.name"
            >
          </v-avatar>
        </template>
        <span>{{ tooltip }}</span>
      </v-tooltip>
    </template>
    <template v-if="displayStyle == 'vs'">
      <span>&nbsp;{{ player.name }}</span>
      <span v-if="elim">({{ elimResult }})</span>
    </template>
    <template v-if="displayStyle == 'table'">
      <v-row no-gutters>
        <v-col cols="6">
          <v-avatar
            :size="48"
            :class="[winResultClass, eliminatedOpacityClass]"
          >
            <img
              :src="player.picture"
              :alt="player.name"
            >
          </v-avatar>
          <span>{{ player.name }}</span>
        </v-col>
        <v-col cols="3">
          {{ elimResult }}
        </v-col>
        <v-col cols="3">
          {{ elimPosition }}
        </v-col>
      </v-row>
    </template>
  </v-col>
</template>
<script>
function winResultValue(winResult) {
  if (winResult === 'WIN') return 1
  if (winResult === 'LOSS') return -1
  if (winResult === 'DRAW') return 0
  throw winResult
}

export default {
  name: "PlayerInGameInfo",
  props: ["player", "displayStyle"],
  computed: {
    cols() {
      if (this.displayStyle === 'vs') {
        return 3;
      }
      if (this.displayStyle === 'avatars') {
        return 2;
      }
      return 12
    },
    winResultClass() {
      if (!this.elim) return 'not-eliminated';
      let winValue = this.elim.winResult
      if (winValue < 0) return 'loser'
      if (winValue > 0) return 'winner'
      return 'draw'
    },
    eliminatedOpacityClass() {
      if (!this.elim) return 'normal'
      if (this.elim.winResult > 0) return 'normal'
      return 'eliminated'
    },
    tooltip() {
      return this.player.name
    },
    elimResult() {
      if (!this.elim) return ''
      let winValue = this.elim.winResult
      if (winValue < 0) return 'LOSE'
      if (winValue > 0) return 'WIN'
      return 'DRAW'
    },
    elimPosition() {
      if (!this.elim) return ''
      return this.elim.position
    },
    elim() {
      let elimination = this.player.elimination
      if (!elimination) return null
      let server = !!elimination.type
      if (server) {
        return {
          position: elimination.position,
          winResult: winResultValue(elimination.winResult)
        }
      }
      return {
        position: elimination.position,
        winResult: elimination.winResult.result
      }
    }
  }
}
</script>
<style scoped>
.player-in-game-info {
    display: inline;
}

.normal {
    opacity: 1;
}

.eliminated {
    opacity: 0.3;
}

.loser {
    box-shadow: 5px 5px 10px 0px rgba(255, 0, 0, 0.75);
}

.draw {
    box-shadow: 5px 5px 10px 0px rgba(255, 255, 0, 0.75);
}

.winner {
    box-shadow: 5px 5px 10px 0px rgba(0, 255, 0, 0.75);
}
</style>