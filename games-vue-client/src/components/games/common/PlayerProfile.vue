<template>
    <div class="player-profile">
        <v-tooltip bottom>
            <template v-slot:activator="{ on }">
                <v-avatar :size="size" v-on="on" :class="[winResultClass, eliminatedOpacityClass]">
                    <img
                        :src="player.picture"
                        :alt="player.name" />
                </v-avatar>
                <span v-if="showName">{{ player.name }} {{ postFix }}</span>
            </template>
            <span>{{ player.name }}</span>
        </v-tooltip>
    </div>
</template>
<script>
export default {
    // Tooltip, Menu/v-router-link (go to profile page), v-avatar, show-name
    name: "PlayerProfile",
    props: {
        player: Object,
        showName: { type: Boolean, default: false },
        size: { type: Number, default: 32 },
        postFix: { type: String, default: "" }
    },
    computed: {
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
</style>