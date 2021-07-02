<template>
  <div class="player-profile" :class="{ highlight: highlight }">
    <v-tooltip bottom>
      <template v-slot:activator="{ on }">
        <v-avatar
          :size="size"
          :class="[winResultClass, eliminatedOpacityClass]"
          v-on="on"
        >
          <img
            :src="playerToDisplay.picture"
            :alt="playerToDisplay.name"
          >
        </v-avatar>
        <span v-if="showName">&nbsp;{{ playerToDisplay.name }} {{ postFix }}</span>
      </template>
      <span><span v-if="typeof playerIndex !== 'undefined'">({{ playerIndex }}) </span> <span>{{ playerToDisplay.name }}</span></span>
    </v-tooltip>
  </div>
</template>
<script>
export default {
    // Tooltip, Menu/v-router-link (go to profile page), v-avatar, show-name
    name: "PlayerProfile",
    props: {
        context: { type: Object, default: null },
        playerIndex: { type: Number, default: null },
        player: { type: Object, default: null },
        showName: { type: Boolean, default: false },
        size: { type: Number, default: 32 },
        highlight: { type: Boolean, default: false },
        postFix: { type: String, default: "" }
    },
    computed: {
        playerToDisplay() {
            if (this.player) return this.player;
            return this.context.players[this.playerIndex]
        },
        elim() {
            return null
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
        }
    }
}
</script>
<style scoped>
div.player-profile {
  display: inline;
}
div.player-profile.highlight {
  border: 3px solid cyan;
}
</style>