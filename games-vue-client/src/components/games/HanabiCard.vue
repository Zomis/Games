<template>
    <v-card elevation="4" class="hanabi-card" :class="{ 'double-view': doubleView, 'actionable': typeof action !== 'undefined', 'highlight': highlight }" :style="{ 'background-color': cardColor }">
        <transition name="slide-fade" mode="out-in">
            <v-card-title class="slide-fade-item others-view" :key="cardValue">
                {{ cardValue }}
            </v-card-title>
        </transition>
        <transition name="slide-fade" mode="out-in">
            <v-card-text class="slide-fade-item player-known" v-if="doubleView" :style="{'background-color': cardKnownColor}" :key="cardKnownValue + '-' + cardKnownColor">
                {{ cardKnownValue }}
            </v-card-text>
        </transition>
        <v-card-actions v-if="action">
            <v-btn @click="action('Play', 'play-' + index)">Play</v-btn>
            <v-btn @click="action('Discard', 'discard-' + index)">Discard</v-btn>
        </v-card-actions>
    </v-card>
</template>
<script>
const colorToDisplayColor = {
    "red": "#ef476f",
    "blue": "#118AB2",
    "green": "#06D6A0",
    "yellow": "#FFD166",
    "white": "#F5F5F5",
    "rainbow": "#FF7F00"
}

export default {
    name: "HanabiCard",
    props: ["card", "index", "action", "doubleView", "highlight"],
    computed: {
        cardKnownValue() {
            if (this.card.valueKnown) {
                return this.card.value
            }
            return '???'
        },
        cardKnownColor() {
            if (this.card.colorKnown) {
                return colorToDisplayColor[this.card.color.toLowerCase()]
            }
            return "grey"
        },
        cardValue() {
            if (this.card.value) {
                return this.card.value
            }
            return '???'
        },
        cardColor() {
            if (this.card.color) {
                return colorToDisplayColor[this.card.color.toLowerCase()]
            }
            return "grey"
        }
    },

}
</script>
<style scoped>
.hanabi-card.actionable {
    width: 160px;
    height: 80px;
}
.hanabi-card.double-view {
    width: 48px;
}
.hanabi-card.double-view .others-view {
    padding: 0 0 8px 16px;
    margin-bottom: 0;
    font-weight: bold;
    font-size: 26px;

}
.hanabi-card.double-view .player-known {
    padding: 0;
    font-weight: bold;
    flex: 1;
    border-top: 1px solid black;
}
.hanabi-card {
    width: 48px;
    border: 1px solid black !important;
    transition: all 1s linear, background-color 2.5s ease;
}
.hanabi-card.highlight {
    border: 1px solid yellowgreen !important;
    box-shadow: 0px 0px 5px 6px yellowgreen !important;
}

.slide-fade-enter-active {
  transition: all .5s ease;
}
.slide-fade-leave-active {
  transition: all .5s ease;
}
.slide-fade-enter, .slide-fade-leave-to {
  transform: translateX(10px);
  opacity: 0;
}

</style>
