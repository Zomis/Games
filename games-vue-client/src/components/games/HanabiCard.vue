<template>
    <v-col>
    <v-card class="hanabi-card" :class="{ 'double-view': doubleView, 'actionable': typeof action !== 'undefined' }" :style="{ 'background-color': 'black' }">
        <v-card-title class="others-view" :class="'color-' + cardColor">
            {{ cardValue }}
        </v-card-title>
        <v-card-text class="player-known" v-if="doubleView" :class="'color-' + cardKnownColor">
            {{ cardKnownValue }}
        </v-card-text>
        <v-card-actions v-if="action">
            <v-btn @click="action('Play', 'play-' + index)">Play</v-btn>
            <v-btn @click="action('Discard', 'discard-' + index)">Discard</v-btn>
        </v-card-actions>
    </v-card>
    </v-col>
</template>
<script>
export default {
    name: "HanabiCard",
    props: ["card", "index", "action", "doubleView"],
    computed: {
        cardKnownValue() {
            if (this.card.valueKnown) {
                return this.card.value
            }
            return '???'
        },
        cardKnownColor() {
            if (this.card.colorKnown) {
                return this.card.color.toLowerCase()
            }
            return 'unknown'
        },

        cardValue() {
            if (this.card.value) {
                return this.card.value
            }
            return '???'
        },
        cardColor() {
            if (this.card.color) {
                return this.card.color.toLowerCase()
            }
            return 'unknown'
        }
    },

}
</script>
<style>
.hanabi-card .color-unknown {
    color: gray !important;
}
.hanabi-card .color-red {
    color: red !important;
}
.hanabi-card .color-green {
    color: lime !important;
}
.hanabi-card .color-blue {
    color: cyan !important;
}
.hanabi-card .color-white {
    color: white !important;
}
.hanabi-card .color-yellow {
    color: yellow !important;
}
.hanabi-card.actionable {
    width: 160px;
    height: 80px;
}
.hanabi-card.double-view {
    width: 48px;
    height: 72px;
}
.hanabi-card.double-view .others-view {
    padding: 0 0 8px 16px;
    margin-bottom: 0;
}
.hanabi-card.double-view .player-known {
    padding: 0
}
.hanabi-card {
    width: 48px;
    height: 48px;
}
</style>
